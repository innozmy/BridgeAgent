package myz.bridge_agent_demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 配置层：本机 Python Agent 的 HTTP 客户端。不走 RestClient 的 JSON 转换，
 * 避免 Spring Boot 4 / Jackson 3 把 {@code files} 列表序列化成空数组。
 */
@Configuration
public class PythonAgentConfig {

    @Bean
    HttpClient pythonAgentHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
