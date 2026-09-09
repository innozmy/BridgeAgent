package myz.bridge_agent_demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 三车道作业线程池：救急线程为 0；池内每条车道最多再等 {@code app.queue.pool-queue-capacity} 个。
 * MySQL {@code queued} 仍是重启后的排队账。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AgentAsyncConfig {

    @Value("${app.queue.drawing-core:2}")
    private int drawingCore;

    @Value("${app.queue.knowledge-core:2}")
    private int knowledgeCore;

    @Value("${app.queue.sap-core:1}")
    private int sapCore;

    @Value("${app.queue.pool-queue-capacity:5}")
    private int poolQueueCapacity;

    @Bean(name = "drawingJobExecutor")
    Executor drawingJobExecutor() {
        return lanePool("drawing-job-", drawingCore, poolQueueCapacity);
    }

    @Bean(name = "knowledgeJobExecutor")
    Executor knowledgeJobExecutor() {
        return lanePool("knowledge-job-", knowledgeCore, poolQueueCapacity);
    }

    @Bean(name = "sapJobExecutor")
    Executor sapJobExecutor() {
        return lanePool("sap-job-", sapCore, poolQueueCapacity);
    }

    private static Executor lanePool(String prefix, int core, int queueCapacity) {
        int size = Math.max(1, core);
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(size);
        pool.setMaxPoolSize(size);
        pool.setQueueCapacity(Math.max(0, queueCapacity));
        pool.setThreadNamePrefix(prefix);
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        pool.initialize();
        return pool;
    }
}
