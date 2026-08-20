package gg.duo.chat.dto;

import gg.duo.common.dto.UserDto;
import java.time.Instant;
import java.util.List;

public class ChatDtos {

    /** applicationId: 방장이 아닌 멤버의 신청 id (확정 버튼용), 방장은 null */
    public record MemberDto(UserDto user, boolean confirmed, boolean owner, Long applicationId) {}

    public record RoomDto(Long id, Long postId, String postTitle, Long postAuthorId,
                          String postStatus, List<MemberDto> members) {}

    /**
     * 방 입장 응답.
     *
     * messages 는 전체가 아니라 가장 최근 한 묶음이다(오래된 것이 앞).
     * hasMore 가 true 면 그 앞에 더 있다는 뜻이고, 클라이언트는 messages 의
     * 첫 메시지 id 를 커서로 /messages?before= 를 호출해 이어 받는다.
     */
    public record RoomDetail(RoomDto room, List<MessageDto> messages, boolean hasMore) {}

    /** 이전 메시지 응답 — 오래된 것이 앞, hasMore 는 그 앞에 더 있는지 */
    public record MessagePage(List<MessageDto> messages, boolean hasMore) {}

    public record MessageDto(Long id, Long roomId, Long senderId, String senderNickname,
                             String content, Instant createdAt) {}

    public record SendRequest(String content) {}
}
