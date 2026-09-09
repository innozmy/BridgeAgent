package myz.bridge_agent_demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 入口。启动后默认监听 8080，给 Vue 前端提供 /api 接口。
 * <p>
 * {@code @MapperScan} 让 MyBatis-Plus 扫描 mapper 包，不必每个接口再配 XML。
 */
@SpringBootApplication
@MapperScan("myz.bridge_agent_demo.mapper")
public class BridgeAgentDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BridgeAgentDemoApplication.class, args);
    }
}
