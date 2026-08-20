package gg.duo.chat.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅(WebSocket/STOMP) 지표.
 *
 * cAdvisor 는 컨테이너를 밖에서 보므로 "backend 가 CPU 를 얼마나 쓰는지"는 알아도
 * "그게 채팅 때문인지"는 알 수 없다. 앱 내부 사정은 앱이 직접 내보내야 한다.
 *
 * Counter 와 Gauge 의 구분:
 *   Counter — 올라가기만 하는 누적값(메시지 수). 조회할 때 rate() 로 감싼다.
 *   Gauge   — 오르내리는 현재값(접속자 수). 그대로 본다.
 * container_cpu_usage_seconds_total(counter) 과
 * container_memory_working_set_bytes(gauge) 의 관계와 동일하다.
 *
 * 주의: userId / IP / roomId 처럼 값의 종류가 무한한 것을 tag 로 넣으면
 * 시계열이 폭발한다(카디널리티). 그런 추적은 로그(Loki)의 역할이다.
 */
@Component
public class ChatMetrics {

    private final Counter messagesIn;
    private final Counter connects;
    private final Counter disconnects;

    /**
     * [동시 접속자를 세는 방식]
     *
     * 전에는 AtomicInteger 를 두고 연결에 +1, 해제에 -1 을 했다.
     * 그 결과 대시보드에 -2 가 찍혔다. 나간 횟수가 들어온 횟수보다 많았다는 뜻이다.
     *
     * 원인은 두 이벤트의 발생 조건이 다르다는 것이다.
     *   SessionConnectedEvent   STOMP CONNECT 핸드셰이크가 "완료"돼야 발생
     *   SessionDisconnectEvent  소켓이 "닫히기만" 하면 발생
     * 그래서 아래 경우에 해제만 세어진다.
     *   - SockJS 가 전송 방식을 시도하다 실패해 세션이 열렸다 닫힐 때
     *   - 클라이언트가 DISCONNECT 프레임을 보내고 소켓도 닫아 두 번 발생할 때
     *   - 백엔드 재시작 직후 남은 연결이 정리될 때
     *
     * 증감 방식은 한 번 어긋나면 영원히 어긋난다. 계산기라서 다음 사람이
     * 정상적으로 들어와도 -2 + 1 = -1 이다.
     *
     * 그래서 "계수기" 대신 "명부"로 바꿨다. 들어올 때 세션 ID 를 적고 나갈 때 지운다.
     * 현재 인원 = 명부에 남은 이름의 수.
     *   중복 해제        이미 지워진 이름 → 무해
     *   연결 없는 해제    없는 이름 → 무해
     *   음수             이름 개수는 0 미만이 될 수 없다 → 구조적으로 불가능
     *
     * 자기 교정도 된다. 어긋나더라도 다음 세션이 들어오는 순간 실제 값이 맞는다.
     *
     * Gauge.builder(..., Set::size) 는 값을 저장하지 않는다. Prometheus 가 30초마다
     * 긁어갈 때마다 그 순간의 명부를 센다. 그래서 과거의 실수가 이월되지 않는다.
     * 대시보드의 다른 지표들(메모리, 커넥션 풀, 디스크 여유)이 전부 "지금 상태"를
     * 직접 재는 것과 같은 방식이다. 이 지표만 혼자 증감식이라 혼자 음수가 났다.
     *
     * ⚠️ sessionId 는 Set 에 담을 뿐 tag 로 쓰지 않는다. tag 로 넣으면 세션마다
     *    시계열이 생겨 카디널리티가 폭발한다. 시계열은 여전히 하나다.
     */
    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();

    public ChatMetrics(MeterRegistry registry) {
        this.messagesIn = Counter.builder("chat_messages_total")
                .description("채팅 메시지 수신 건수")
                .tag("direction", "inbound")
                .register(registry);

        this.connects = Counter.builder("chat_connection_events_total")
                .description("WebSocket 연결/해제 이벤트")
                .tag("event", "connect")
                .register(registry);

        this.disconnects = Counter.builder("chat_connection_events_total")
                .description("WebSocket 연결/해제 이벤트")
                .tag("event", "disconnect")
                .register(registry);

        Gauge.builder("chat_active_sessions", activeSessionIds, Set::size)
                .description("현재 열려 있는 채팅 세션 수")
                .register(registry);
    }

    /** @MessageMapping 핸들러에서 호출 */
    public void messageReceived() {
        messagesIn.increment();
    }

    /**
     * connect/disconnect counter 는 그대로 둔다.
     * 누적 이벤트 수는 "해제가 급증했다"(네트워크 불안정 / 이상 종료)를 보는 데 쓰이고,
     * 두 counter 를 비교하면 위에서 말한 비대칭이 실제로 얼마나 일어나는지도 알 수 있다.
     *   sum by (event) (increase(chat_connection_events_total[1h]))
     */
    public void sessionOpened(String sessionId) {
        connects.increment();
        if (sessionId != null) {
            activeSessionIds.add(sessionId);
        }
    }

    public void sessionClosed(String sessionId) {
        disconnects.increment();
        if (sessionId != null) {
            activeSessionIds.remove(sessionId);
        }
    }
}
