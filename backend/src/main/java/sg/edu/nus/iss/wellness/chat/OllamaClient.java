package sg.edu.nus.iss.wellness.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import sg.edu.nus.iss.wellness.common.ApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 *
 * 轻量 HTTP 客户端，调用本地 Ollama 的 /api/chat 端点（非流式，stream:false）。
 * 不依赖 WebClient / RestTemplate，用 JDK 11+ 内置的 java.net.http.HttpClient，
 * 零额外依赖，适合这个 toy 项目。
 *
 * 调用链：
 *   ChatService.chat(userId, message)
 *     → OllamaClient.chat(systemPrompt, userMessage)
 *       → POST http://localhost:11434/api/chat
 *       → 返回 assistant 回复文本
 *
 * PROJECT_SPEC.md §5：stream:false，模型由配置决定（llama3.1:8b 或 qwen2.5:7b）。
 */
@Component
public class OllamaClient {

    /** Ollama /api/chat 响应的顶层结构（只关心 message.content） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaResponse(OllamaMessage message) {}

    /** Ollama message 对象 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaMessage(String role, String content) {}

    private static final int TIMEOUT_SECONDS = 120; // 本地 LLM 推理可能较慢，给足时间

    private final OllamaProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(OllamaProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        // 每个实例共用一个 HttpClient（线程安全，内部维护连接池）
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 向 Ollama 发送一次对话请求，返回模型的文字回复。
     *
     * @param systemPrompt  System role 的提示词（注入用户健康数据上下文）
     * @param userMessage   用户这条消息的原文
     * @return              模型回复的纯文本
     */
    public String chat(String systemPrompt, String userMessage) {
        // 构造 Ollama /api/chat 要求的请求体：
        // {"model":"llama3.1:8b","stream":false,"messages":[{"role":"system",...},{"role":"user",...}]}
        Map<String, Object> requestBody = Map.of(
                "model", props.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userMessage)
                )
        );

        try {
            String json = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                // Ollama 返回非 200，比如模型不存在时会返回 404
                throw ApiException.badRequest(
                        "Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }

            OllamaResponse ollamaResponse = objectMapper.readValue(response.body(), OllamaResponse.class);

            if (ollamaResponse.message() == null || ollamaResponse.message().content() == null) {
                throw ApiException.badRequest("Ollama response has no message content");
            }

            return ollamaResponse.message().content().trim();

        } catch (ApiException e) {
            throw e; // 直接往上抛，让 GlobalExceptionHandler 处理
        } catch (Exception e) {
            // 网络超时、Ollama 未启动、JSON 解析失败等都在这里统一包装
            throw ApiException.badRequest("failed to reach Ollama: " + e.getMessage());
        }
    }
}
