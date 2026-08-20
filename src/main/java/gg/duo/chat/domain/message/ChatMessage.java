package gg.duo.chat.domain.message;

import gg.duo.chat.domain.room.ChatRoom;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    /** users.id — user 서비스 소유. */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /**
     * 보낸 시점의 닉네임·프로필 이미지 스냅샷.
     *
     * 이건 성능 때문이 아니라 의미 때문에 복제한다. 나중에 닉네임을 바꿔도
     * 과거 메시지에는 그때 이름이 남는 게 자연스럽다 — 대화 기록이란 그런 것이다.
     * (그래서 UserProfileUpdatedEvent 를 받아도 여기는 갱신하지 않는다)
     *
     * 부수 효과로 메시지 50개를 그릴 때 user 서비스 호출이 0번이 된다.
     */
    @Column(name = "sender_nickname")
    private String senderNickname;

    @Column(name = "sender_profile_image")
    private String senderProfileImage;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
