package sg.edu.nus.iss.wellness.chat;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.chat.dto.ChatRequest;
import sg.edu.nus.iss.wellness.chat.dto.ChatResponse;
import sg.edu.nus.iss.wellness.chat.dto.ChatbotMessageResponse;
import sg.edu.nus.iss.wellness.user.User;

import java.util.List;

/**
 *
 * 对应 PROJECT_SPEC.md §4 Chatbot 接口：
 *   POST /api/chat         — 发消息，拿回 AI 回复
 *   GET  /api/chat/history — 拿该用户的完整对话历史
 *
 * @AuthenticationPrincipal User 由 JwtAuthFilter 注入，见 SecurityConfig。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 发送消息给健康助理。
     * 后端会自动注入近 7 天数据做上下文，再调 Ollama 生成回复。
     * 用户消息和 AI 回复都会存入 chatbot_messages 表。
     */
    @PostMapping
    public ChatResponse chat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequest request) {
        String reply = chatService.chat(user.getId(), request.message());
        return new ChatResponse(reply);
    }

    /**
     * 拉取该用户的全部对话历史，按时间升序。
     * 前端按 role 字段区分气泡：user → 右，assistant → 左。
     */
    @GetMapping("/history")
    public List<ChatbotMessageResponse> history(@AuthenticationPrincipal User user) {
        return chatService.history(user.getId());
    }
}
