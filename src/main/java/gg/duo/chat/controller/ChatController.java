package gg.duo.chat.controller;

import gg.duo.chat.dto.ChatDtos.MessagePage;
import gg.duo.chat.dto.ChatDtos.RoomDetail;
import gg.duo.chat.dto.ChatDtos.RoomDto;
import gg.duo.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/rooms")
    public List<RoomDto> myRooms(Authentication auth) {
        return chatService.myRooms((Long) auth.getPrincipal());
    }

    /** 방 입장 — 방 정보 + 가장 최근 메시지 한 묶음 */
    @GetMapping("/rooms/{roomId}")
    public RoomDetail roomDetail(@PathVariable Long roomId, Authentication auth) {
        return chatService.roomDetail(roomId, (Long) auth.getPrincipal());
    }

    /**
     * 이전 메시지 — before 로 받은 메시지 id 보다 앞선 것들.
     *
     * before 를 생략하면 가장 최근 묶음을 돌려준다(방 입장과 같은 결과).
     */
    @GetMapping("/rooms/{roomId}/messages")
    public MessagePage messages(@PathVariable Long roomId,
                                @RequestParam(required = false) Long before,
                                @RequestParam(required = false) Integer size,
                                Authentication auth) {
        return chatService.messagesBefore(roomId, before, size, (Long) auth.getPrincipal());
    }

    /** 방장: 멤버 강퇴 (신청 거절 처리 포함) */
    @DeleteMapping("/rooms/{roomId}/members/{userId}")
    public void kick(@PathVariable Long roomId, @PathVariable Long userId, Authentication auth) {
        chatService.kick(roomId, userId, (Long) auth.getPrincipal());
    }
}
