package gg.duo.chat.config;

import gg.duo.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;

    // application.yml 의 rabbitmq.* 값을 주입받는다.
    @Value("${rabbitmq.host}")
    private String relayHost;

    @Value("${rabbitmq.port}")
    private int relayPort;

    @Value("${rabbitmq.username}")
    private String relayUsername;

    @Value("${rabbitmq.password}")
    private String relayPassword;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 기존: registry.enableSimpleBroker("/topic");
        // 변경: 서버 메모리 대신 외부 RabbitMQ(STOMP)로 메시지 전달을 위임한다.
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                // 클라이언트 세션이 브로커에 연결할 때 쓰는 계정
                .setClientLogin(relayUsername)
                .setClientPasscode(relayPassword)
                // 애플리케이션(시스템)이 브로커에 연결할 때 쓰는 계정
                .setSystemLogin(relayUsername)
                .setSystemPasscode(relayPassword);

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // JWT 인증 인터셉터 — 브로커 교체와 무관하며 그대로 유지된다.
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer ")
                            || !jwtTokenProvider.validate(header.substring(7))) {
                        throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
                    }
                    Long userId = jwtTokenProvider.getUserId(header.substring(7));
                    accessor.setUser(new UsernamePasswordAuthenticationToken(
                            String.valueOf(userId), null, List.of()));
                }
                return message;
            }
        });
    }
}
