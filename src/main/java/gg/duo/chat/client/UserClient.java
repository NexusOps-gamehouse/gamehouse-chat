package gg.duo.chat.client;

import gg.duo.common.dto.UserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * user 서비스 조회 — 방 멤버 목록에만 쓴다.
 *
 * 메시지 목록에는 쓰지 않는다. 발신자 닉네임·프로필은 보낸 시점 스냅샷이
 * chat_messages 에 남아 있어서, 메시지 50개를 그려도 호출이 0번이다.
 * 멤버 목록은 "지금 이 사람의 티어·온라인 여부"를 보여줘야 해서 최신값이 필요하다.
 *
 * (설계서의 chat 모듈에는 client/ 가 없었다. 멤버 목록 응답이 UserDto 를 통째로
 *  담고 있어 — 프론트가 그 형태를 쓴다 — 최신값 조회처가 필요해 추가했다.)
 */
@Slf4j
@Component
public class UserClient {

    private static final ParameterizedTypeReference<List<UserDto>> USER_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public UserClient(RestClient.Builder builder,
                      @Value("${services.user.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Map<Long, UserDto> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            List<UserDto> users = restClient.get()
                    .uri(uri -> uri.path("/internal/users").queryParam("ids", ids).build())
                    .retrieve()
                    .body(USER_LIST);
            if (users == null) return Map.of();
            return users.stream().collect(Collectors.toMap(UserDto::id, Function.identity()));
        } catch (RestClientException e) {
            // user 가 죽어도 대화는 이어져야 한다. 멤버 정보만 비운다.
            log.warn("user 서비스 조회 실패 — 멤버 정보 없이 응답한다. ids={}", ids, e);
            return Map.of();
        }
    }
}
