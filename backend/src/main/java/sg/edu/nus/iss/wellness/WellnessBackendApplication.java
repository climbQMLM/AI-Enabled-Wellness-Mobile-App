package sg.edu.nus.iss.wellness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 *
 * @ConfigurationPropertiesScan 让 Spring 自动扫描并注册所有
 * @ConfigurationProperties 类（JwtProperties、OllamaProperties），
 * 不用再为每一个都手动加 @EnableConfigurationProperties(Xxx.class)。
 *
 * @EnableScheduling 开启 @Scheduled 支持，用于 AgentService 的每周定时任务。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class WellnessBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(WellnessBackendApplication.class, args);
	}

}
