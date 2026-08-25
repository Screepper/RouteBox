package com.routebox.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
    * @Description WebSocket message executor thread pool configuration
    * @Date 2026/3/14
    * @Author Screep
**/
@Configuration
public class WebsocketThreadPoolConfig {
    @Bean("wsMessageExecutor")
    public ThreadPoolTaskExecutor wsMessageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("websocket-manager-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }
}
