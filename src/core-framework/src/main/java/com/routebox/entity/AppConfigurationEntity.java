package com.routebox.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "")
public class AppConfigurationEntity {
    private BaseService elasticsearch;
    private BaseService grafana;
    private Topology topology;
    private AnomalyDetect anomalyDetect;
    private String baseFilePath;

    @Data
    public static class BaseService {
        private String ip;
        private int port;
        private String username;
        private String password;
    }

    @Data
    public static class Topology {
        private String sernesId;
        private Ws ws;

        @Data
        public static class Ws {
            private long timeoutS;
            private int maxRetries;
            private long retryWaitMs;
            private int maxConnections;
        }
    }

    @Data
    public static class AnomalyDetect {
        private String url;
        private int detectWindow;
        private int combineSeconds;
        private int periodInterval;
    }
}