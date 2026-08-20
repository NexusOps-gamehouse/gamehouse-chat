package gg.duo.chat.service;

import gg.duo.chat.client.UserClient;
import gg.duo.chat.domain.message.ChatMessage;
import gg.duo.chat.domain.message.ChatMessageRepository;
import gg.duo.chat.domain.room.ChatRoom;
import gg.duo.chat.domain.room.ChatRoomMember;
import gg.duo.chat.domain.room.ChatRoomMemberRepository;
import gg.duo.chat.domain.room.ChatRoomRepository;
import gg.duo.chat.dto.ChatDtos.MemberDto;
import gg.duo.chat.dto.ChatDtos.MessageDto;
import gg.duo.chat.dto.ChatDtos.MessagePage;
import gg.duo.chat.dto.ChatDtos.RoomDetail;
import gg.duo.chat.dto.ChatDtos.RoomDto;
import gg.duo.chat.event.publisher.ChatEventPublisher;
import gg.duo.common.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 사라진 의존 두 개:
     *   UserRepository        → UserClient (users 는 user 소유)
     *   ApplicationRepository → 이벤트 (applications 는 post 소유)
     *
     * ApplicationRepository 를 쓰던 자리는 두 군데였다.
     *   1) 강퇴 시 신청 거절 처리 → ChatMemberKickedEvent 발행
     *   2) 멤버의 신청 id 조회    → chat_room_members.application_id 복제본
     * 이 의존이 chat → post 방향이었고, post → chat 과 합쳐져 순환을 만들었다.
     */
    private final UserClient userClient;
    private final ChatEventPublisher events;

    @Transactional(readOnly = true)
    public List<RoomDto> myRooms(Long meId) {
        return chatRoomMemberRepository.findByUserIdOrderByIdDesc(meId)
                .stream().map(m -> toRoomDto(m.getRoom())).toList();
    }

    /** 방 입장 시 한 번에 내려주는 메시지 수 */
    private static final int PAGE_SIZE = 50;

    /** 한 번에 요청할 수 있는 최대 메시지 수 */
    private static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    public RoomDetail roomDetail(Long roomId, Long meId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        assertMember(room.getId(), meId);

        Slice page = loadMessages(roomId, null, PAGE_SIZE);
        return new RoomDetail(toRoomDto(room), page.messages(), page.hasMore());
    }

    /**
     * 이전 메시지 — beforeId 보다 앞선 것들.
     *
     * 방을 여는 시점 기준이 아니라 "이 메시지보다 앞"으로 요청하므로,
     * 그 사이에 새 메시지가 도착해도 경계가 밀리지 않는다.
     */
    @Transactional(readOnly = true)
    public MessagePage messagesBefore(Long roomId, Long beforeId, Integer size, Long meId) {
        assertMember(roomId, meId);
        int limit = (size == null) ? PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Slice page = loadMessages(roomId, beforeId, limit);
        return new MessagePage(page.messages(), page.hasMore());
    }

    private record Slice(List<MessageDto> messages, boolean hasMore) {}

    /**
     * 최신순으로 limit + 1 개를 읽어 hasMore 를 판단하고, 화면 순서(오래된 것이 앞)로
     * 뒤집어 돌려준다.
     *
     * 하나를 더 읽는 이유: 별도의 count 쿼리 없이 "더 있는지"를 알 수 있다.
     * limit + 1 개가 나왔다면 넘치는 하나를 버리고 hasMore = true 로 둔다.
     */
    private Slice loadMessages(Long roomId, Long beforeId, int limit) {
        Pageable probe = PageRequest.of(0, limit + 1);
        List<ChatMessage> found = (beforeId == null)
                ? chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, probe)
                : chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, probe);

        boolean hasMore = found.size() > limit;
        List<ChatMessage> page = hasMore ? found.subList(0, limit) : found;

        // 조회는 최신순(desc), 화면은 오래된 것이 위 → 뒤집는다.
        List<MessageDto> messages = new ArrayList<>(page.size());
        for (int i = page.size() - 1; i >= 0; i--) {
            messages.add(toMessageDto(page.get(i)));
        }
        return new Slice(messages, hasMore);
    }

    @Transactional
    public MessageDto saveMessage(Long roomId, Long senderId, String content) {
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("메시지를 입력해주세요.");
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        assertMember(room.getId(), senderId);

        ChatMessage msg = new ChatMessage();
        msg.setRoom(room);
        msg.setSenderId(senderId);

        // 보낸 시점의 닉네임을 함께 남긴다. 나중에 닉네임을 바꿔도 과거 메시지에는
        // 그때 이름이 남는 게 자연스럽고, 덕분에 메시지 목록을 그릴 때
        // user 서비스를 한 번도 부르지 않는다.
        UserDto sender = userClient.findAllByIds(List.of(senderId)).get(senderId);
        if (sender != null) {
            msg.setSenderNickname(sender.nickname());
            msg.setSenderProfileImage(sender.profileImageUrl());
        }

        msg.setContent(content.trim());
        chatMessageRepository.save(msg);
        return toMessageDto(msg);
    }

    /** 방장: 멤버 강퇴 (채팅방 제거 + 신청 거절 처리) */
    @Transactional
    public void kick(Long roomId, Long targetUserId, Long meId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        if (!room.getOwnerId().equals(meId))
            throw new SecurityException("방장만 내보낼 수 있습니다.");
        if (room.getOwnerId().equals(targetUserId))
            throw new IllegalStateException("방장은 내보낼 수 없습니다.");

        ChatRoomMember member = chatRoomMemberRepository
                .findByRoomIdAndUserId(roomId, targetUserId).orElseThrow();
        chatRoomMemberRepository.delete(member);

        // applications 는 post 소유다. 상태 변경을 직접 하지 않고 사실만 알린다.
        events.memberKicked(room, targetUserId);

        events.notify(targetUserId,
                "'" + room.getPostTitle() + "' 파티에서 내보내졌습니다.", null);
    }

    private void assertMember(Long roomId, Long userId) {
        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId))
            throw new SecurityException("채팅방 참여자가 아닙니다.");
    }

    private RoomDto toRoomDto(ChatRoom room) {
        List<ChatRoomMember> members = chatRoomMemberRepository
                .findByRoomIdOrderByJoinedAtAsc(room.getId());

        // 멤버 정보는 최신값이어야 한다(티어·온라인 여부). id 를 모아 한 번에 가져온다.
        Map<Long, UserDto> users = userClient.findAllByIds(
                members.stream().map(ChatRoomMember::getUserId).distinct().toList());

        List<MemberDto> memberDtos = members.stream().map(m -> {
            boolean owner = room.getOwnerId().equals(m.getUserId());
            // 신청 id 는 승인 이벤트가 실어다 준 복제본에서 읽는다.
            // 예전에는 멤버마다 applications 를 조회했다(= 이제는 HTTP 왕복).
            Long applicationId = owner ? null : m.getApplicationId();
            return new MemberDto(users.get(m.getUserId()), m.isConfirmed(), owner, applicationId);
        }).toList();

        return new RoomDto(room.getId(), room.getPostId(), room.getPostTitle(),
                room.getOwnerId(), room.getPostStatus(), memberDtos);
    }

    private MessageDto toMessageDto(ChatMessage m) {
        return new MessageDto(m.getId(), m.getRoom().getId(), m.getSenderId(),
                m.getSenderNickname(), m.getContent(), m.getCreatedAt());
    }
}
