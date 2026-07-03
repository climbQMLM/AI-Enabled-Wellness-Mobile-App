package sg.edu.nus.iss.wellness.wellness.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 *
 * 一个 wake-date 当天所有睡眠段（夜间主睡 + 可能的午睡）累加后的结果。
 * 故意做成可变类（不是 record）——因为同一天可能要 accumulate 好几次，
 * 见 PROJECT_SPEC.md §3.2 第3条："归属日期 = Wake-up time 的日期；
 * 同一 wake-date 的多段累加 Time Asleep / 各 Stages / Awake，
 * sleep_ratio 取加权平均"。
 *
 * sleep_ratio 的加权平均以 Time Asleep(min) 作为权重——睡得越久的那一段，
 * 它的睡眠效率对当天整体的代表性应该越高，这是本项目对"加权平均"的具体定义
 * （spec 没有规定权重用什么，这里选 Time Asleep 是最自然的选择）。
 */
public class SleepAggRow {

    private int timeAsleepMin = 0;
    private int deepSleepMin = 0;
    private int remSleepMin = 0;
    private int lightSleepMin = 0;
    private int awakeMin = 0;

    // 加权平均的分子分母：sum(ratio_i * weight_i) / sum(weight_i)，权重是该段的 Time Asleep
    private BigDecimal ratioWeightedSum = BigDecimal.ZERO;
    private long ratioWeightTotal = 0;

    public void accumulate(int timeAsleepMin, int deepSleepMin, int remSleepMin,
                            int lightSleepMin, int awakeMin, BigDecimal sleepRatio) {
        this.timeAsleepMin += timeAsleepMin;
        this.deepSleepMin += deepSleepMin;
        this.remSleepMin += remSleepMin;
        this.lightSleepMin += lightSleepMin;
        this.awakeMin += awakeMin;

        if (sleepRatio != null && timeAsleepMin > 0) {
            ratioWeightedSum = ratioWeightedSum.add(sleepRatio.multiply(BigDecimal.valueOf(timeAsleepMin)));
            ratioWeightTotal += timeAsleepMin;
        }
    }

    public Integer getTimeAsleepMin() { return timeAsleepMin; }
    public Integer getDeepSleepMin() { return deepSleepMin; }
    public Integer getRemSleepMin() { return remSleepMin; }
    public Integer getLightSleepMin() { return lightSleepMin; }
    public Integer getAwakeMin() { return awakeMin; }

    public BigDecimal getSleepRatio() {
        if (ratioWeightTotal == 0) {
            return null;
        }
        return ratioWeightedSum.divide(BigDecimal.valueOf(ratioWeightTotal), 2, RoundingMode.HALF_UP);
    }
}
