package sg.edu.nus.iss.wellness.wellness.dto;

import java.math.BigDecimal;

/**
 *
 * PUT /api/wellness/{id} 的请求体，PATCH 语义（见 PROJECT_SPEC.md §4）：
 * 字段为 null 表示"调用方没传，保持原值不变"，不是"把这个字段清空成 NULL"。
 * 如果以后真要支持"显式清空某个字段"，得换成 Optional<Integer> 之类的
 * 三态(unset/null/value)包装，目前 spec 没要求，先不做。
 *
 * logDate 故意不放进来——日期是这条记录的身份标识(uq_user_date)，
 * 改日期等价于"挪到另一天"，语义上更接近删了重建，不通过 PATCH 支持。
 */
public record WellnessLogPatchRequest(
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
