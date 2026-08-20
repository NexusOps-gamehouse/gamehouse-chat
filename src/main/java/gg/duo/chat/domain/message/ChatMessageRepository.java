package gg.duo.chat.domain.message;

import gg.duo.chat.domain.message.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // -----------------------------------------------------------------------
    // 메시지 조회는 커서(keyset) 방식이다.
    //
    // 예전에는 findByRoomIdOrderByCreatedAtAsc(roomId) 로 방의 메시지를 전부
    // 읽었다. 채팅은 대화만 하면 자동으로 쌓이므로, 상한이 없으면 오래된 방일수록
    // 방을 여는 요청 하나가 점점 무거워진다. content 는 TEXT 다.
    //
    // 게시글 목록처럼 page/size(offset)를 쓰지 않는 이유:
    // 메시지는 실시간으로 계속 추가된다. "이전 50개"를 요청하는 사이에 새 메시지가
    // 도착하면 offset 경계가 그만큼 밀려, 이미 본 메시지를 다시 받거나
    // 못 본 메시지를 건너뛴다. 기준을 고정된 id 로 잡으면 그런 일이 없다.
    //
    // 커서로 createdAt 이 아니라 id 를 쓰는 이유:
    // id 는 IDENTITY 라 단조 증가한다. createdAt 은 같은 시각에 저장된 두 메시지의
    // 순서를 가릴 수 없어 경계에서 흔들린다.
    //
    // 두 메서드 모두 최신순(id desc)으로 가져온다. 화면은 오래된 것이 위이므로
    // 서비스에서 뒤집는다.
    // -----------------------------------------------------------------------

    /** 방 입장 시 — 가장 최근 메시지부터 Pageable 크기만큼 */
    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    /** 이전 메시지 — beforeId 보다 앞선 것 중 가장 최근부터 Pageable 크기만큼 */
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeId,
                                                             Pageable pageable);

    void deleteByRoomId(Long roomId);
}
