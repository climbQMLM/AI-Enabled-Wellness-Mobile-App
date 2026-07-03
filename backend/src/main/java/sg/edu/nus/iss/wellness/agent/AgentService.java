package sg.edu.nus.iss.wellness.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.wellness.agent.dto.RecommendationResponse;
import sg.edu.nus.iss.wellness.chat.OllamaClient;
import sg.edu.nus.iss.wellness.user.UserRepository;
import sg.edu.nus.iss.wellness.wellness.WellnessLog;
import sg.edu.nus.iss.wellness.wellness.WellnessLogRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 *
 * Agentic 工作流，对应 PROJECT_SPEC.md §7 的五步：
 *
 *   1. retrieve  — 查近 7 天 wellness_logs
 *   2. analyze   — 算 HRV 斜率、RHR 斜率、平均睡眠、平均 readiness
 *   3. decide    — 规则判断恢复状态（疲劳 / 良好 / 维持）
 *   4. generate  — 把状态 + 数据交给 Ollama 生成个性化建议（强约束只返回 JSON）
 *   5. save+show — 存 recommendations 表，返回给调用方
 *
 * 触发方式：
 *   a. POST /api/agent/run （用户主动触发，只跑当前用户）
 *   b. @Scheduled 每周一凌晨 2 点（自动定时，遍历所有用户）
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final WellnessLogRepository wellnessLogRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public AgentService(WellnessLogRepository wellnessLogRepository,
                        RecommendationRepository recommendationRepository,
                        UserRepository userRepository,
                        OllamaClient ollamaClient,
                        ObjectMapper objectMapper,
                        EntityManager entityManager) {
        this.wellnessLogRepository = wellnessLogRepository;
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 公共接口
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 手动触发：为指定用户跑一次完整的 agentic 工作流，返回新生成的建议。
     * POST /api/agent/run 调用这里。
     */
    @Transactional
    public RecommendationResponse runForUser(Long userId) {
        Recommendation rec = runAgentWorkflow(userId);
        entityManager.flush();
        entityManager.refresh(rec);
        return RecommendationResponse.from(rec);
    }

    /**
     * 查询某用户在 [from, to] 日期区间内的历史建议，按日期降序（最新的在前）。
     * GET /api/recommendations?from&to 调用这里。
     */
    public List<RecommendationResponse> list(Long userId, LocalDate from, LocalDate to) {
        LocalDate effectiveTo   = (to   != null) ? to   : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(29);

        return recommendationRepository
                .findByUserIdAndRecDateBetweenOrderByRecDateDesc(userId, effectiveFrom, effectiveTo)
                .stream()
                .map(RecommendationResponse::from)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 定时任务：每周一 02:00 为所有用户各跑一次
    // cron = "秒 分 时 日 月 周"，0 0 2 * * MON = 每周一 02:00:00
    // ─────────────────────────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 2 * * MON")
    public void scheduledWeeklyRun() {
        log.info("[Agent Scheduler] 开始每周定时 agent 运行");
        // 遍历所有注册用户，逐一触发 agent 工作流
        // 每个用户独立事务（runForUser 带 @Transactional），某个用户失败不影响其他人
        userRepository.findAll().forEach(user -> {
            try {
                runForUser(user.getId());
                log.info("[Agent Scheduler] userId={} 完成", user.getId());
            } catch (Exception e) {
                // 单个用户失败（比如 Ollama 超时）只记日志，不中断整个批次
                log.error("[Agent Scheduler] userId={} 失败: {}", user.getId(), e.getMessage());
            }
        });
        log.info("[Agent Scheduler] 每周定时 agent 运行结束");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §7 五步工作流
    // ─────────────────────────────────────────────────────────────────────────

    private Recommendation runAgentWorkflow(Long userId) {

        // ── Step 1: retrieve ──────────────────────────────────────────────────
        // 取近 7 天（含今天）的 wellness_logs
        LocalDate today   = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);
        List<WellnessLog> logs = wellnessLogRepository
                .findByUserIdAndLogDateBetweenOrderByLogDateAsc(userId, weekAgo, today);

        // ── Step 2: analyze ───────────────────────────────────────────────────
        Analysis analysis = analyze(logs);

        // ── Step 3: decide ────────────────────────────────────────────────────
        // 规则判断（见 PROJECT_SPEC.md §7）：
        //   HRV 持续下降 + RHR 上升 + 睡眠 < 360min(6h) → 疲劳/恢复不足
        //   readiness 均值上升趋势 + 睡眠充足(≥ 420min/7h) → 状态良好
        //   否则 → 维持现状
        String state;
        if (analysis.hrvSlope < 0 && analysis.rhrSlope > 0
                && analysis.avgSleepMin < 360) {
            state = "疲劳/恢复不足，建议减量休息";
        } else if (analysis.readinessSlope > 0 && analysis.avgSleepMin >= 420) {
            state = "状态良好，可正常或适量增加训练";
        } else {
            state = "维持现状";
        }

        // ── Step 4: generate ──────────────────────────────────────────────────
        // 强约束 Ollama 只输出 JSON，便于后续解析入库
        String systemPrompt = buildAgentSystemPrompt(analysis, state);
        String userPrompt =
                "请根据以上数据和状态判断，生成一条简短的个性化健康建议。" +
                "只输出 JSON：{\"summary\": \"...\", \"recommendation\": \"...\"}，不要任何多余文字。";

        String rawReply = ollamaClient.chat(systemPrompt, userPrompt);
        String content  = extractRecommendationText(rawReply, state, analysis);

        // ── Step 5: save ──────────────────────────────────────────────────────
        Recommendation rec = new Recommendation(
                userId,
                today,
                Recommendation.Type.recovery,
                Recommendation.CreatedBy.agent,
                content
        );
        return recommendationRepository.save(rec);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2 细节：近 7 天数据的统计量
    // ─────────────────────────────────────────────────────────────────────────

    /** 聚合后的分析结果，传给 step 3（规则判断）和 step 4（prompt 构造） */
    private record Analysis(
            double hrvSlope,        // HRV 变化方向（正 = 上升，负 = 下降）
            double rhrSlope,        // 静息心率变化方向（正 = 上升，负 = 下降）
            double readinessSlope,  // readiness_score 变化方向
            double avgSleepMin,     // 平均睡眠分钟数（无数据时为 0）
            double avgHrv,          // 平均 HRV
            double avgRhr,          // 平均静息心率
            double avgReadiness,    // 平均 readiness 分
            int    dataPoints       // 有多少天有数据
    ) {}

    private Analysis analyze(List<WellnessLog> logs) {
        if (logs.isEmpty()) {
            return new Analysis(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // 斜率用"最后一天均值 - 最前一天均值"来近似，足够判断趋势方向
        // 比做线性回归简单，对于 7 天数据误差可接受
        double firstHrv       = firstNonNull(logs, WellnessLog::getHrvAvg);
        double lastHrv        = lastNonNull(logs, WellnessLog::getHrvAvg);
        double firstRhr       = firstNonNull(logs, WellnessLog::getHrMin);
        double lastRhr        = lastNonNull(logs, WellnessLog::getHrMin);
        double firstReadiness = firstNonNull(logs, WellnessLog::getReadinessScore);
        double lastReadiness  = lastNonNull(logs, WellnessLog::getReadinessScore);

        double avgSleep     = avg(logs, l -> l.getTimeAsleepMin() == null ? null : (double) l.getTimeAsleepMin());
        double avgHrv       = avg(logs, l -> l.getHrvAvg()        == null ? null : (double) l.getHrvAvg());
        double avgRhr       = avg(logs, l -> l.getHrMin()         == null ? null : (double) l.getHrMin());
        double avgReadiness = avg(logs, l -> l.getReadinessScore() == null ? null : (double) l.getReadinessScore());

        return new Analysis(
                lastHrv       - firstHrv,
                lastRhr       - firstRhr,
                lastReadiness - firstReadiness,
                avgSleep,
                avgHrv,
                avgRhr,
                avgReadiness,
                logs.size()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 4 细节：构造 agentic system prompt
    // ─────────────────────────────────────────────────────────────────────────

    private String buildAgentSystemPrompt(Analysis a, String state) {
        return """
                你是一个运动恢复分析助理。请严格基于以下数据给出建议，不要编造数据。

                近 7 天健康指标（共 %d 天有数据）：
                  - 平均睡眠：%.0f 分钟/天
                  - 平均 HRV：%.0f ms（变化趋势：%s）
                  - 平均静息心率：%.0f bpm（变化趋势：%s）
                  - 平均 Readiness：%.0f / 100（变化趋势：%s）

                系统判断当前状态：%s"""
                .formatted(
                        a.dataPoints(),
                        a.avgSleepMin(),
                        a.avgHrv(),       trend(a.hrvSlope()),
                        a.avgRhr(),       trend(-a.rhrSlope()), // RHR 下降对身体是好事，反转显示
                        a.avgReadiness(), trend(a.readinessSlope()),
                        state
                );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 4 细节：从 Ollama 回复里提取 recommendation 字段
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ollama 被要求只返回 JSON，但实际上有时会在 JSON 前后夹杂文字。
     * 这里做容错：
     *   1. 找到第一个 '{' 到最后一个 '}' 之间的内容当 JSON 解析
     *   2. 取 "recommendation" 字段（没有则取 "summary"）
     *   3. 再没有就直接用 Ollama 原始回复（截断到 500 字）
     *   4. 如果 Ollama 调用整个失败（比如未启动），用规则生成兜底文本
     */
    private String extractRecommendationText(String rawReply, String state, Analysis a) {
        try {
            // 找 JSON 片段（容忍前后的废话文字）
            int start = rawReply.indexOf('{');
            int end   = rawReply.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonStr = rawReply.substring(start, end + 1);
                JsonNode node = objectMapper.readTree(jsonStr);

                // 优先取 recommendation，其次 summary
                if (node.has("recommendation") && !node.get("recommendation").asText().isBlank()) {
                    return node.get("recommendation").asText().trim();
                }
                if (node.has("summary") && !node.get("summary").asText().isBlank()) {
                    return node.get("summary").asText().trim();
                }
            }
        } catch (Exception ignored) {
            // JSON 解析失败，往下走兜底逻辑
        }

        // Ollama 没给出可解析的 JSON，直接用原始文本（截断防止 content 字段太长）
        if (rawReply != null && !rawReply.isBlank()) {
            return rawReply.length() > 500 ? rawReply.substring(0, 500) + "…" : rawReply;
        }

        // 最终兜底：完全基于规则生成一句话
        return String.format("【%s】近 7 天平均睡眠 %.0f 分钟，平均 HRV %.0f ms，请注意调整训练强度。",
                state, a.avgSleepMin(), a.avgHrv());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Extractor { Double extract(WellnessLog log); }

    /** 从有序 logs 里找第一个非 null 值（取最早的有效数据） */
    private double firstNonNull(List<WellnessLog> logs, java.util.function.Function<WellnessLog, Number> getter) {
        return logs.stream()
                .map(getter)
                .filter(v -> v != null)
                .mapToDouble(Number::doubleValue)
                .findFirst()
                .orElse(0.0);
    }

    /** 从有序 logs 里找最后一个非 null 值（取最新的有效数据） */
    private double lastNonNull(List<WellnessLog> logs, java.util.function.Function<WellnessLog, Number> getter) {
        double result = 0.0;
        for (WellnessLog log : logs) {
            Number val = getter.apply(log);
            if (val != null) result = val.doubleValue();
        }
        return result;
    }

    /** 对指定字段求平均，跳过 null，全 null 返回 0 */
    private double avg(List<WellnessLog> logs, Extractor extractor) {
        OptionalDouble avg = logs.stream()
                .map(extractor::extract)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .average();
        return avg.orElse(0.0);
    }

    /** 把斜率数字转成人类可读的趋势描述 */
    private String trend(double slope) {
        if (slope > 1)  return "上升";
        if (slope < -1) return "下降";
        return "平稳";
    }
}
