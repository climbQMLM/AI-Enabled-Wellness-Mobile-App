package sg.edu.nus.iss.wellness.wellness.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 *
 * POST /api/wellness 手动录入的请求体。只有 logDate 是必填，其余字段
 * "只填子集"——对应 PROJECT_SPEC.md §2 的设计原则。所有可选字段用包装类型
 * (Integer/BigDecimal)，不传就是 null，落库时该字段也是 NULL。
 */
public record WellnessLogCreateRequest(

        @NotNull(message = "must not be null")
        LocalDate logDate,

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
        BigDecimal sleepRatio
) {
}
