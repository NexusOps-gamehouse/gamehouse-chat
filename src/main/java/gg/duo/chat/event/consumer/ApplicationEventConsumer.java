package gg.duo.chat.event.consumer;

import gg.duo.chat.service.ChatRoomProvisioningService;
import gg.duo.common.event.ApplicationApprovedEvent;
import gg.duo.common.event.ApplicationConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 참가 신청 처리 결과를 채팅방 멤버십에 반영한다.
 *
 * 승인 → 방 입장, 확정 → confirmed 플래그. 두 단계를 나누는 이유는
 * 채팅방 입장에는 인원 제한이 없고(누구나 들러서 이야기할 수 있다),
 * 방장이 "확정"을 눌러야 비로소 한 자리가 채워지기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class ApplicationEventConsumer {

    private final ChatRoomProvisioningService provisioning;

    @EventListener
    public void on(ApplicationApprovedEvent e) {
        provisioning.addMember(e.postId(), e.applicantId(), e.applicationId());
    }

    @EventListener
    public void on(ApplicationConfirmedEvent e) {
        provisioning.confirmMember(e.postId(), e.applicantId());
    }
}
