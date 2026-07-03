package sg.edu.nus.iss.wellness.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 *
 * chatbot_messages 表的数据访问层。
 * findByUserId 用于 GET /api/chat/history，按创建时间升序返回
 * （最早的消息排前面，前端按顺序渲染对话气泡）。
 */
public interface ChatbotMessageRepository extends JpaRepository<ChatbotMessage, Long> {

    List<ChatbotMessage> findByUserIdOrderByCreatedAtAsc(Long userId);
}
