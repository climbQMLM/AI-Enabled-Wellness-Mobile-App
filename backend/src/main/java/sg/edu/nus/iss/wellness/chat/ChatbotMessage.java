package sg.edu.nus.iss.wellness.chat;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 *
 * 对应 schema.sql 里的 chatbot_messages 表，保存每一条对话记录。
 * role='user' 是用户发送的消息，role='assistant' 是 Ollama 回复的消息。
 * 前端根据 role 决定气泡靠左（assistant）还是靠右（user）。
 */
@Entity
@Table(name = "chatbot_messages")
public class ChatbotMessage {

    /** 消息的说话方角色，对应 DB 的 ENUM('user','assistant') */
    public enum Role { user, assistant }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户，存 FK 值即可，不需要 @ManyToOne 关联（保持极简） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 消息角色：user = 用户输入，assistant = 模型回复 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    /** 消息正文 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * DB 端 DEFAULT CURRENT_TIMESTAMP 自动填，Java 侧设为不可插入/更新。
     * 读取时由 entityManager.refresh() 或 select 带回来。
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 要求的无参构造 */
    protected ChatbotMessage() {}

    public ChatbotMessage(Long userId, Role role, String content) {
        this.userId = userId;
        this.role = role;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
