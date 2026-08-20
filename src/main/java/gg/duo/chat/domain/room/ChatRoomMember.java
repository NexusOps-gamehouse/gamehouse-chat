package gg.duo.chat.domain.room;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "chat_room_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 같은 chat 서비스 안이라 연관을 유지한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    /** users.id — user 서비스 소유. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 이 멤버를 들어오게 한 참가 신청 번호.
     *
     * 예전에는 화면의 "확정" 버튼을 그리려고 방 멤버마다 post 서비스에
     * "이 사람의 신청 id 가 뭐냐"를 물어야 했다(멤버 수만큼 왕복). 승인
     * 이벤트가 이미 이 값을 실어 오므로 그때 받아 적어둔다.
     *
     * 방장은 신청 없이 방에 있으므로 null 이다.
     */
    @Column(name = "application_id")
    private Long applicationId;

    @Column(nullable = false)
    private boolean confirmed = false; // 방장이 모집 확정한 멤버인지

    @Column(nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();
}
