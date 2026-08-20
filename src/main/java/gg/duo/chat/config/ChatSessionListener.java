package gg.duo.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket 세션 연결/해제를 받아 지표를 갱신한다.
 *
 * Spring 이 STOMP 세션 생명주기마다 이벤트를 발행하므로
 * WebSocketConfig 를 수정하지 않고 리스너만 추가하면 된다.
 *
 * 해제 빈도가 급증하면 네트워크 불안정 / 이상 종료 / 타임아웃 신호로 읽는다.
 *
 * 세션 ID 를 함께 넘기는 이유는 ChatMetrics 의 주석을 참고. 요약하면,
 * 연결/해제 이벤트가 1:1 로 짝을 이루지 않아 증감식으로는 접속자 수가 음수가 된다.
 */
@Component
@RequiredArgsConstructor
public class ChatSessionListener {

    private final ChatMetrics chatMetrics;

    /**
     * SessionConnectedEvent 에는 getSessionId() 가 없다.
     * STOMP 헤더가 담긴 메시지를 열어 꺼내야 한다.
     */
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        chatMetrics.sessionOpened(sessionId);
    }

    /** 이쪽은 이벤트가 세션 ID 를 직접 제공한다. */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        chatMetrics.sessionClosed(event.getSessionId());
    }
}
