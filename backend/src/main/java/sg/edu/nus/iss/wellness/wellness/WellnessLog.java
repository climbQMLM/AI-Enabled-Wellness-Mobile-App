package sg.edu.nus.iss.wellness.wellness;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 *
 * 映射 db/schema.sql 里的 wellness_logs 表——每个 (user_id, log_date) 一行，
 * 字段分组对应 RingConn 的三个 CSV + 自算的 readiness_score，详细含义见
 * PROJECT_SPEC.md §2。这里只对 JPA 映射本身做注释。
 *
 * 故意不用 @ManyToOne User user 这种关联映射，只存一个裸的 userId Long——
 * 这个项目里 wellness_logs 从不需要联表查 user 的其它字段（邮箱/昵称），
 * 用关联映射只会徒增懒加载/N+1 的复杂度，不如直接存 ID 简单。
 */
@Entity
@Table(name = "wellness_logs")
public class WellnessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private Source source;

    // ===== Activity =====
    private Integer steps;
    @Column(name = "calories_kcal")
    private Integer caloriesKcal;

    // ===== Vital Signs =====
    @Column(name = "hr_avg")
    private Integer hrAvg;
    @Column(name = "hr_min")
    private Integer hrMin;
    @Column(name = "hr_max")
    private Integer hrMax;

    @Column(name = "spo2_avg")
    private Integer spo2Avg;
    @Column(name = "spo2_min")
    private Integer spo2Min;
    @Column(name = "spo2_max")
    private Integer spo2Max;

    @Column(name = "hrv_avg")
    private Integer hrvAvg;
    @Column(name = "hrv_min")
    private Integer hrvMin;
    @Column(name = "hrv_max")
    private Integer hrvMax;

    // ===== Sleep =====
    @Column(name = "time_asleep_min")
    private Integer timeAsleepMin;
    @Column(name = "deep_sleep_min")
    private Integer deepSleepMin;
    @Column(name = "rem_sleep_min")
    private Integer remSleepMin;
    @Column(name = "light_sleep_min")
    private Integer lightSleepMin;
    @Column(name = "awake_min")
    private Integer awakeMin;
    @Column(name = "sleep_ratio")
    private BigDecimal sleepRatio;

    // ===== 自算 =====
    @Column(name = "readiness_score")
    private Integer readinessScore;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** 对应 schema 里的 ENUM('manual','ringconn')，名字必须和 DB 枚举值完全一致 */
    public enum Source {
        manual, ringconn
    }

    protected WellnessLog() {
        // JPA 用
    }

    public WellnessLog(Long userId, LocalDate logDate, Source source) {
        this.userId = userId;
        this.logDate = logDate;
        this.source = source;
    }

    // ===== getters =====

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getLogDate() { return logDate; }
    public Source getSource() { return source; }
    public Integer getSteps() { return steps; }
    public Integer getCaloriesKcal() { return caloriesKcal; }
    public Integer getHrAvg() { return hrAvg; }
    public Integer getHrMin() { return hrMin; }
    public Integer getHrMax() { return hrMax; }
    public Integer getSpo2Avg() { return spo2Avg; }
    public Integer getSpo2Min() { return spo2Min; }
    public Integer getSpo2Max() { return spo2Max; }
    public Integer getHrvAvg() { return hrvAvg; }
    public Integer getHrvMin() { return hrvMin; }
    public Integer getHrvMax() { return hrvMax; }
    public Integer getTimeAsleepMin() { return timeAsleepMin; }
    public Integer getDeepSleepMin() { return deepSleepMin; }
    public Integer getRemSleepMin() { return remSleepMin; }
    public Integer getLightSleepMin() { return lightSleepMin; }
    public Integer getAwakeMin() { return awakeMin; }
    public BigDecimal getSleepRatio() { return sleepRatio; }
    public Integer getReadinessScore() { return readinessScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // ===== setters（只给 Service 层用，做字段级更新） =====

    public void setSource(Source source) { this.source = source; }
    public void setSteps(Integer steps) { this.steps = steps; }
    public void setCaloriesKcal(Integer caloriesKcal) { this.caloriesKcal = caloriesKcal; }
    public void setHrAvg(Integer hrAvg) { this.hrAvg = hrAvg; }
    public void setHrMin(Integer hrMin) { this.hrMin = hrMin; }
    public void setHrMax(Integer hrMax) { this.hrMax = hrMax; }
    public void setSpo2Avg(Integer spo2Avg) { this.spo2Avg = spo2Avg; }
    public void setSpo2Min(Integer spo2Min) { this.spo2Min = spo2Min; }
    public void setSpo2Max(Integer spo2Max) { this.spo2Max = spo2Max; }
    public void setHrvAvg(Integer hrvAvg) { this.hrvAvg = hrvAvg; }
    public void setHrvMin(Integer hrvMin) { this.hrvMin = hrvMin; }
    public void setHrvMax(Integer hrvMax) { this.hrvMax = hrvMax; }
    public void setTimeAsleepMin(Integer timeAsleepMin) { this.timeAsleepMin = timeAsleepMin; }
    public void setDeepSleepMin(Integer deepSleepMin) { this.deepSleepMin = deepSleepMin; }
    public void setRemSleepMin(Integer remSleepMin) { this.remSleepMin = remSleepMin; }
    public void setLightSleepMin(Integer lightSleepMin) { this.lightSleepMin = lightSleepMin; }
    public void setAwakeMin(Integer awakeMin) { this.awakeMin = awakeMin; }
    public void setSleepRatio(BigDecimal sleepRatio) { this.sleepRatio = sleepRatio; }
    public void setReadinessScore(Integer readinessScore) { this.readinessScore = readinessScore; }
}
