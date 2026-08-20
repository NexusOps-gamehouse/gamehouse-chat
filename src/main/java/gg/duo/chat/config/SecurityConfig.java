package gg.duo.chat.config;

import gg.duo.common.security.SecurityBaseConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends SecurityBaseConfig {

    @Override
    protected void configurePublicEndpoints(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        // WebSocket 핸드셰이크는 HTTP 필터가 아니라 STOMP CONNECT 프레임에서
        // 인증한다(WebSocketConfig.configureClientInboundChannel).
        // 여기서 막으면 SockJS 협상 자체가 401 로 끊긴다.
        auth.requestMatchers("/ws/**").permitAll();
        auth.requestMatchers("/internal/**").permitAll();
    }
}
