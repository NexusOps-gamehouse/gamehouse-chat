package gg.duo.chat.event.publisher;

import gg.duo.chat.domain.room.ChatRoom;
import gg.duo.common.event.ChatMemberKickedEvent;
import gg.duo.common.event.ChatRoomCreatedEvent;
import gg.duo.common.event.DomainEventPublisher;
import gg.duo.common.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final DomainEventPublisher publisher;

    /** 방을 열었다 → post 가 posts.chat_room_id 를 채운다. */
    public void roomCreated(ChatRoom room) {
        publisher.publish(new ChatRoomCreatedEvent(
                room.getId(), room.getPostId(), room.getOwnerId()));
    }

    /** 강퇴 → post 가 해당 참가 신청을 거절 처리한다. */
    public void memberKicked(ChatRoom room, Long targetUserId) {
        publisher.publish(new ChatMemberKickedEvent(
                room.getId(), room.getPostId(), targetUserId));
    }

    /** 알림 요청 — notifications 는 user 소유다. */
    public void notify(Long userId, String message, String link) {
        publisher.publish(new NotificationRequestedEvent(userId, message, link));
    }
}
