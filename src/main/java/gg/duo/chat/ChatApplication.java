package gg.duo.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * chat 서비스 — 채팅 · WebSocket.
 *
 * 소유 테이블: chat_rooms, chat_room_members, chat_messages
 *
 * 이 서비스를 따로 떼는 이득이 가장 크다. WebSocket 세션은 오래 살아 있어서
 * 재배포 때마다 전원이 재접속한다. 한 덩어리였을 때는 로그인 로직을 한 줄
 * 고쳐도 채팅 중인 사람이 전부 끊겼다.
 */
@SpringBootApplication(scanBasePackages = {"gg.duo.chat", "gg.duo.common"})
public class ChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
