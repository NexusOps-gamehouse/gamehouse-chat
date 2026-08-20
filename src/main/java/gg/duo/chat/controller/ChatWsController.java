package gg.duo.chat.controller;

import gg.duo.chat.config.ChatMetrics;
import gg.duo.chat.dto.ChatDtos.MessageDto;
import gg.duo.chat.dto.ChatDtos.SendRequest;
import gg.duo.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMetrics chatMetrics;

    /** 클라이언트 → /app/rooms/{roomId} 로 전송, 구독자는 /topic/rooms/{roomId} 수신 */
    @MessageMapping("/rooms/{roomId}")
    public void send(@DestinationVariable Long roomId, @Payload SendRequest request,
                     Principal principal) {
        chatMetrics.messageReceived();
        Long senderId = Long.parseLong(principal.getName());
        MessageDto saved = chatService.saveMessage(roomId, senderId, request.content());
        messagingTemplate.convertAndSend("/topic/rooms." + roomId, saved);
    }
}
