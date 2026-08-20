package gg.duo.chat.service;

import gg.duo.chat.domain.message.ChatMessageRepository;
import gg.duo.chat.domain.room.ChatRoom;
import gg.duo.chat.domain.room.ChatRoomMember;
import gg.duo.chat.domain.room.ChatRoomMemberRepository;
import gg.duo.chat.domain.room.ChatRoomRepository;
import gg.duo.chat.event.publisher.ChatEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트를 받아 방을 만들고 정리하는 쪽.
 *
 * 사용자 요청으로 도는 ChatService 와 분리했다. 두 가지가 다르다.
 *   - 실패 처리: 사용자 요청은 예외를 던져 400/403 을 돌려주면 되지만,
 *     이벤트 처리는 예외를 던지면 재시도 대상이 된다. 그래서 여기 메서드는
 *     전부 멱등이다 — 같은 이벤트를 두 번 받아도 결과가 같다.
 *   - 권한 검사: 이벤트는 이미 post 서비스가 권한을 확인하고 발행한 사실이라
 *     여기서 다시 검사하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatRoomProvisioningService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatEventPublisher events;

    /**
     * 이 글의 방이 있는지 확인하고 없으면 만든다.
     *
     * 방장은 만들 때 바로 멤버(confirmed=true)로 넣는다. 글을 만든 시점에
     * 이미 한 자리를 차지하기 때문이다.
     */
    @Transactional
    public ChatRoom ensureRoom(Long postId, Long ownerId, String postTitle, String postStatus) {
        ChatRoom room = chatRoomRepository.findByPostId(postId).orElse(null);
        if (room != null) {
            // 멱등: 이미 있으면 스냅샷만 최신으로 맞춘다.
            room.setPostTitle(postTitle);
            room.setPostStatus(postStatus);
            return room;
        }

        room = new ChatRoom();
        room.setPostId(postId);
        room.setOwnerId(ownerId);
        room.setPostTitle(postTitle);
        room.setPostStatus(postStatus);
        chatRoomRepository.save(room);

        ChatRoomMember owner = new ChatRoomMember();
        owner.setRoom(room);
        owner.setUserId(ownerId);
        owner.setConfirmed(true);
        chatRoomMemberRepository.save(owner);

        events.roomCreated(room);
        return room;
    }

    /** 모집글의 제목·상태 스냅샷 갱신 */
    @Transactional
    public void updateSnapshot(Long postId, String postTitle, String postStatus) {
        chatRoomRepository.findByPostId(postId).ifPresent(room -> {
            room.setPostTitle(postTitle);
            room.setPostStatus(postStatus);
        });
    }

    /** 승인된 신청자를 멤버로 넣는다. 이미 있으면 신청 번호만 채운다. */
    @Transactional
    public void addMember(Long postId, Long userId, Long applicationId) {
        ChatRoom room = chatRoomRepository.findByPostId(postId).orElse(null);
        if (room == null) return; // 방이 아직 없다 — PostCreatedEvent 가 재시도되면 이어진다.

        chatRoomMemberRepository.findByRoomIdAndUserId(room.getId(), userId)
                .ifPresentOrElse(
                        m -> m.setApplicationId(applicationId),
                        () -> {
                            ChatRoomMember member = new ChatRoomMember();
                            member.setRoom(room);
                            member.setUserId(userId);
                            member.setApplicationId(applicationId);
                            chatRoomMemberRepository.save(member);
                        });
    }

    /** 확정 처리 — 멤버의 confirmed 플래그를 올린다. */
    @Transactional
    public void confirmMember(Long postId, Long userId) {
        chatRoomRepository.findByPostId(postId).ifPresent(room ->
                chatRoomMemberRepository.findByRoomIdAndUserId(room.getId(), userId)
                        .ifPresent(m -> m.setConfirmed(true)));
    }

    /**
     * 글이 지워졌다 — 딸린 방·멤버·메시지를 정리한다.
     *
     * FK 참조 순서대로 지운다 (메시지 → 멤버 → 방).
     * 이미 지워졌으면 아무 일도 하지 않는다(멱등).
     */
    @Transactional
    public void deleteRoomOfPost(Long postId) {
        chatRoomRepository.findByPostId(postId).ifPresent(room -> {
            chatMessageRepository.deleteByRoomId(room.getId());
            chatRoomMemberRepository.deleteByRoomId(room.getId());
            chatRoomRepository.delete(room);
        });
    }
}
