package sg.edu.nus.iss.wellness.agent;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 *
 * 对应 schema.sql 里的 recommendations 表。
 * 每次 agent 跑完就往这里写一条记录，前端在"推荐"页展示。
 *
 * type     : 固定为 'recovery'（本项目只做恢复分析这一种）
 * created_by: 固定为 'agent'（另一个枚举值 'chatbot' 暂不使用）
 * content  : Ollama 生成的自然语言建议文本
 */
@Entity
@Table(name = "recommendations")
public class Recommendation {

    /** 对应 DB 的 ENUM('recovery','chatbot') */
    public enum Type { recovery, chatbot }

    /** 对应 DB 的 ENUM('agent','chatbot') */
    public enum CreatedBy { agent, chatbot }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户，存 FK 值，不走 @ManyToOne（保持极简） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 建议对应的日期（通常为 agent 运行当天） */
    @Column(name = "rec_date", nullable = false)
    private LocalDate recDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false, length = 16)
    private CreatedBy createdBy;

    /** Ollama 生成的个性化建议正文 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** DB 端 DEFAULT CURRENT_TIMESTAMP，Java 侧不插入/更新，读时由 refresh 带回 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Recommendation() {}

    public Recommendation(Long userId, LocalDate recDate, Type type, CreatedBy createdBy, String content) {
        this.userId = userId;
        this.recDate = recDate;
        this.type = type;
        this.createdBy = createdBy;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getRecDate() { return recDate; }
    public Type getType() { return type; }
    public CreatedBy getCreatedBy() { return createdBy; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
