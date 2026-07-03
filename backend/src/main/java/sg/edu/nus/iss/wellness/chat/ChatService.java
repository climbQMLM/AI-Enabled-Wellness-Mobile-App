package sg.edu.nus.iss.wellness.chat;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.wellness.chat.dto.ChatbotMessageResponse;
import sg.edu.nus.iss.wellness.wellness.WellnessLog;
import sg.edu.nus.iss.wellness.wellness.WellnessLogRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 *
 * 聊天业务逻辑，对应 PROJECT_SPEC.md §5 Chatbot 部分：
 *   1. 查出该用户近 7 天的 wellness_logs
 *   2. 聚合成均值填进 System prompt 模板（数据缺失时填 "N/A"，不捏造数据）
 *   3. 调 OllamaClient 拿到回复
 *   4. 把"用户消息"和"AI 回复"都存进 chatbot_messages 表（用于历史记录）
 *   5. 返回 AI 回复
 */
@Service
public class ChatService {

    private final WellnessLogRepository wellnessLogRepository;
    private final ChatbotMessageRepository chatbotMessageRepository;
    private final OllamaClient ollamaClient;
    private final EntityManager entityManager;

    public ChatService(WellnessLogRepository wellnessLogRepository,
                       ChatbotMessageRepository chatbotMessageRepository,
                       OllamaClient ollamaClient,
                       EntityManager entityManager) {
        this.wellnessLogRepository = wellnessLogRepository;
        this.chatbotMessageRepository = chatbotMessageRepository;
        this.ollamaClient = ollamaClient;
        this.entityManager = entityManager;
    }

    /**
     * 发一条消息给健康助理，返回 AI 回复。
     *
     * @param userId  当前登录用户的 ID
     * @param message 用户输入的消息原文
     * @return        Ollama 生成的回复文本
     */
    @Transactional
    public String chat(Long userId, String message) {
        // 取近 7 天数据（包含今天），用于构造 context
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6); // 包含今天共 7 天
        List<WellnessLog> recentLogs = wellnessLogRepository
                .findByUserIdAndLogDateBetweenOrderByLogDateAsc(userId, weekAgo, today);

        String systemPrompt = buildSystemPrompt(recentLogs);

        // 先存用户消息，再调 Ollama，保证即使 Ollama 超时用户消息也能落库
        ChatbotMessage userMsg = new ChatbotMessage(userId, ChatbotMessage.Role.user, message);
        chatbotMessageRepository.save(userMsg);

        // 调 Ollama（可能较慢，最长等 120s）
        String reply = ollamaClient.chat(systemPrompt, message);

        // 存 AI 回复
        ChatbotMessage assistantMsg = new ChatbotMessage(userId, ChatbotMessage.Role.assistant, reply);
        chatbotMessageRepository.save(assistantMsg);

        // flush 让 DB 生成 created_at，避免返回的历史记录里时间戳为 null
        entityManager.flush();

        return reply;
    }

    /**
     * 返回该用户的完整对话历史，按时间升序（最旧的消息在前）。
     */
    public List<ChatbotMessageResponse> history(Long userId) {
        return chatbotMessageRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(ChatbotMessageResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 用近 7 天数据构造 System prompt，对应 PROJECT_SPEC.md §5 的 prompt 模板：
     *
     *   System: 你是一个健康助理。仅基于用户提供的近 7 天可穿戴数据给出建议，
     *           数据缺失时不要编造。建议要具体、可执行、简短。
     *   Context: 用户近 7 天指标：
     *     - 平均睡眠 {avg_sleep} 分钟，深睡 {deep}，REM {rem}
     *     - 平均 HRV {hrv} ms，静息心率(取 hr_min)均值 {rhr} bpm
     *     - 平均步数 {steps}，平均血氧 {spo2}%
     *
     * 任何字段若全部为 null（佩戴设备天数为 0），填 "N/A"，不编造数值。
     */
    private String buildSystemPrompt(List<WellnessLog> logs) {
        String avgSleep   = avgInt(logs.stream()
                .map(WellnessLog::getTimeAsleepMin).collect(Collectors.toList()));
        String avgDeep    = avgInt(logs.stream()
                .map(WellnessLog::getDeepSleepMin).collect(Collectors.toList()));
        String avgRem     = avgInt(logs.stream()
                .map(WellnessLog::getRemSleepMin).collect(Collectors.toList()));
        String avgHrv     = avgInt(logs.stream()
                .map(WellnessLog::getHrvAvg).collect(Collectors.toList()));
        String avgRhr     = avgInt(logs.stream()
                .map(WellnessLog::getHrMin).collect(Collectors.toList()));
        String avgSteps   = avgInt(logs.stream()
                .map(WellnessLog::getSteps).collect(Collectors.toList()));
        String avgSpo2    = avgDecimal(logs.stream()
                .map(l -> l.getSpo2Avg() == null ? null : new BigDecimal(l.getSpo2Avg()))
                .collect(Collectors.toList()));

        return """
                你是一个健康助理。仅基于用户提供的近 7 天可穿戴数据给出建议，\
                数据缺失时不要编造。建议要具体、可执行、简短。
                用户近 7 天指标：
                  - 平均睡眠 %s 分钟，深睡 %s 分钟，REM %s 分钟
                  - 平均 HRV %s ms，静息心率（hr_min 均值）%s bpm
                  - 平均步数 %s，平均血氧 %s%%"""
                .formatted(avgSleep, avgDeep, avgRem, avgHrv, avgRhr, avgSteps, avgSpo2);
    }

    /** 对 Integer 列表取均值，全 null 返回 "N/A" */
    private String avgInt(List<Integer> values) {
        OptionalDouble avg = values.stream()
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .average();
        return avg.isPresent()
                ? String.valueOf((int) Math.round(avg.getAsDouble()))
                : "N/A";
    }

    /** 对 BigDecimal 列表取均值，全 null 返回 "N/A" */
    private String avgDecimal(List<BigDecimal> values) {
        OptionalDouble avg = values.stream()
                .filter(v -> v != null)
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        return avg.isPresent()
                ? new BigDecimal(avg.getAsDouble()).setScale(1, RoundingMode.HALF_UP).toPlainString()
                : "N/A";
    }
}
