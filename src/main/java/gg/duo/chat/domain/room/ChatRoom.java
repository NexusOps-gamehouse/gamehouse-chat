package gg.duo.chat.domain.room;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 모집글당 하나 생성되는 파티(그룹) 채팅방 */
@Entity
@Table(name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** posts.id — post 서비스 소유. 연관이 아니라 값으로만 들고 있다. */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** users.id — user 서비스 소유. */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /**
     * 모집글 제목·상태 스냅샷.
     *
     * 채팅방 화면 상단이 "어느 글의 방이고 아직 모집 중인지"를 보여준다.
     * 방을 열 때마다 post 에 물으면 왕복이 한 번 더 생기고, post 가 죽으면
     * 채팅까지 안 열린다. PostCreatedEvent / PostUpdatedEvent 로 갱신한다.
     *
     * 메시지의 발신자 닉네임과 달리 이건 "최신값"이어야 한다 —
     * 제목이 바뀌면 방 제목도 따라 바뀌는 게 맞다.
     */
    @Column(name = "post_title")
    private String postTitle;

    @Column(name = "post_status", length = 32)
    private String postStatus;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
