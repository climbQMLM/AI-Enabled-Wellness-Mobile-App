package sg.edu.nus.iss.wellness.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 *
 * 映射 db/schema.sql 里的 users 表。
 * 字段含义见 PROJECT_SPEC.md §2 — 这里不重复注释表结构本身，
 * 只标注 JPA 映射时需要注意的点。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 对应 MySQL 的 AUTO_INCREMENT
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // 注意：这里存的永远是 BCrypt 哈希后的密文，字段名特意叫 passwordHash
    // 而不是 password，提醒所有读到这个类的人不要直接拿它和明文比较，
    // 必须通过 PasswordEncoder.matches() 校验。
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    // insertable=false/updatable=false：这个字段由数据库的
    // DEFAULT CURRENT_TIMESTAMP 自动填充，Java 端不手动赋值，
    // 也不允许后续 update 语句去改它。
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {
        // JPA 要求的无参构造函数，外部代码不要直接 new User() 后手动 set，
        // 统一走下面的全参构造函数，保证创建时字段不缺漏。
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
