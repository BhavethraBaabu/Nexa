package com.nexa.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * Bounded pool for background analyses: a burst of requests queues instead of exhausting
     * threads or provider rate limits. {@code nexa.ai.run-inline=true} (tests) runs jobs on the
     * calling thread so results are visible as soon as the request returns.
     */
    @Bean
    TaskExecutor analysisExecutor(@Value("${nexa.ai.run-inline:false}") boolean runInline) {
        if (runInline) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Separate pool so slow Jira/Slack calls never delay analyses (and vice versa). */
    @Bean
    TaskExecutor actionTaskExecutor(@Value("${nexa.ai.run-inline:false}") boolean runInline) {
        if (runInline) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("action-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
