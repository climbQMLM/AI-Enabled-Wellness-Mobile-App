package sg.edu.nus.iss.wellness.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *
 * 绑定 application.yml 里的 ollama.* 配置：
 *   ollama:
 *     base-url: http://localhost:11434
 *     model: llama3.1:8b
 *
 * 在 WellnessBackendApplication 上已有 @ConfigurationPropertiesScan，
 * 所以这里加 @ConfigurationProperties 就能自动注册，不需要 @Component。
 */
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    /** Ollama HTTP 地址，不含路径，例如 http://localhost:11434 */
    private String baseUrl;

    /** 要调用的模型名，例如 llama3.1:8b 或 qwen2.5:7b */
    private String model;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
