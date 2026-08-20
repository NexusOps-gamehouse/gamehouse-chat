package gg.duo.chat.domain.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    List<ChatRoomMember> findByRoomIdOrderByJoinedAtAsc(Long roomId);
    List<ChatRoomMember> findByUserIdOrderByIdDesc(Long userId);
    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
    long countByRoomId(Long roomId);
    void deleteByRoomId(Long roomId);
}
