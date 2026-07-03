package sg.edu.nus.iss.wellness.wellness;

/**
 *
 * 实现 PROJECT_SPEC.md §6 / §6.1 的 readiness score 算法。
 * 这是一个纯函数，不依赖数据库/Spring，方便单独测试，也方便以后 CSV 导入
 * (§3) 复用同一套计算逻辑——不要在两个地方各写一份公式。
 */
public final class ReadinessCalculator {

    private ReadinessCalculator() {
        // 工具类，不允许实例化
    }

    private static final double HRV_WEIGHT = 0.4;
    private static final double RHR_WEIGHT = 0.3;
    private static final double SLEEP_WEIGHT = 0.3;

    /**
     * @param hrvAvg         平均 HRV，单位 ms，可为 null（缺项不计）
     * @param hrMin          全天最低心率（静息心率），单位 bpm，可为 null
     * @param timeAsleepMin  总睡眠时长，单位分钟，可为 null
     * @return 0-100 的 readiness score；三项全缺时返回 null（不要写死为 0，
     *         避免误导用户"今天恢复很差"——见 PROJECT_SPEC.md §6.1）
     */
    public static Integer compute(Integer hrvAvg, Integer hrMin, Integer timeAsleepMin) {
        double weightedSum = 0;
        double weightTotal = 0;

        if (hrvAvg != null) {
            weightedSum += clamp((hrvAvg - 20) / (80.0 - 20.0)) * HRV_WEIGHT;
            weightTotal += HRV_WEIGHT;
        }
        if (hrMin != null) {
            weightedSum += clamp((70 - hrMin) / (70.0 - 45.0)) * RHR_WEIGHT;
            weightTotal += RHR_WEIGHT;
        }
        if (timeAsleepMin != null) {
            weightedSum += clamp(timeAsleepMin / 480.0) * SLEEP_WEIGHT;
            weightTotal += SLEEP_WEIGHT;
        }

        if (weightTotal == 0) {
            return null;
        }

        // 缺项重新归一权重：剩余项按原比例除以 weightTotal，等价于
        // PROJECT_SPEC.md §6.1 伪代码里的 norm * (w / weightSum) 再求和
        return (int) Math.round(100 * (weightedSum / weightTotal));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
