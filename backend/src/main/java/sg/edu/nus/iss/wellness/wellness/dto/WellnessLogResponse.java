package sg.edu.nus.iss.wellness.wellness.dto;

import sg.edu.nus.iss.wellness.wellness.WellnessLog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 *
 * GET/POST/PUT /api/wellness 统一返回这个形状，字段名用 camelCase
 * （Jackson 默认序列化规则），对应 PROJECT_SPEC.md §2 表结构的每一列。
 */
public record WellnessLogResponse(
        Long id,
        LocalDate logDate,
        String source,

        Integer steps,
        Integer caloriesKcal,

        Integer hrAvg,
        Integer hrMin,
        Integer hrMax,

        Integer spo2Avg,
        Integer spo2Min,
        Integer spo2Max,

        Integer hrvAvg,
        Integer hrvMin,
        Integer hrvMax,

        Integer timeAsleepMin,
        Integer deepSleepMin,
        Integer remSleepMin,
        Integer lightSleepMin,
        Integer awakeMin,
        BigDecimal sleepRatio,

        Integer readinessScore,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static WellnessLogResponse from(WellnessLog log) {
        return new WellnessLogResponse(
                log.getId(),
                log.getLogDate(),
                log.getSource().name(),
                log.getSteps(),
                log.getCaloriesKcal(),
                log.getHrAvg(),
                log.getHrMin(),
                log.getHrMax(),
                log.getSpo2Avg(),
                log.getSpo2Min(),
                log.getSpo2Max(),
                log.getHrvAvg(),
                log.getHrvMin(),
                log.getHrvMax(),
                log.getTimeAsleepMin(),
                log.getDeepSleepMin(),
                log.getRemSleepMin(),
                log.getLightSleepMin(),
                log.getAwakeMin(),
                log.getSleepRatio(),
                log.getReadinessScore(),
                log.getCreatedAt(),
                log.getUpdatedAt()
        );
    }
}
