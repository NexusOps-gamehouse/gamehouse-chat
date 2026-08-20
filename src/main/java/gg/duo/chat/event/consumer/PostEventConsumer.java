package gg.duo.chat.event.consumer;

import gg.duo.chat.service.ChatRoomProvisioningService;
import gg.duo.common.event.PostCreatedEvent;
import gg.duo.common.event.PostDeletedEvent;
import gg.duo.common.event.PostUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * post 가 알려온 사실들을 채팅방에 반영한다.
 *
 * @EventListener 는 1단계(프로세스 내 배달)용이다. 3단계에서 RabbitMQ 로
 * 바꿀 때 @RabbitListener 로 바뀌고 메서드 본문은 그대로다.
 */
@Component
@RequiredArgsConstructor
public class PostEventConsumer {

    private final ChatRoomProvisioningService provisioning;

    @EventListener
    public void on(PostCreatedEvent e) {
        provisioning.ensureRoom(e.postId(), e.authorId(), e.title(), e.status());
    }

    @EventListener
    public void on(PostUpdatedEvent e) {
        provisioning.updateSnapshot(e.postId(), e.title(), e.status());
    }

    @EventListener
    public void on(PostDeletedEvent e) {
        provisioning.deleteRoomOfPost(e.postId());
    }
}
