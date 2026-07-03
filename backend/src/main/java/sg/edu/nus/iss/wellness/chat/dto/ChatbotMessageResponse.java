package sg.edu.nus.iss.wellness.chat.dto;

import sg.edu.nus.iss.wellness.chat.ChatbotMessage;

import java.time.LocalDateTime;

/**
 *
 * GET /api/chat/history 里每条消息的响应体，前端用 role 区分气泡方向：
 * role='user' → 右侧气泡，role='assistant' → 左侧气泡。
 */
public record ChatbotMessageResponse(
        Long id,
        String role,      // "user" 或 "assistant"
        String content,
        LocalDateTime createdAt
) {
    public static ChatbotMessageResponse from(ChatbotMessage msg) {
        return new ChatbotMessageResponse(
                msg.getId(),
                msg.getRole().name(),
                msg.getContent(),
                msg.getCreatedAt()
        );
    }
}
