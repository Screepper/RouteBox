package com.routebox.common.constants;

import com.routebox.entity.AppConfigurationEntity;
import com.routebox.manager.TopologyManager;
import org.springframework.stereotype.Component;

/**
 * @Description Dynamic parameters from application-dev.yaml and fix after the application starts
 * @Date 2025/12/17
**/
@Component
public class ConfConstants {
    private static AppConfigurationEntity app;

    public ConfConstants(AppConfigurationEntity app) {
        ConfConstants.app = app;

        if (!TopologyManager.init()) {
            System.err.println("TopologyInfo is null or no running network system initialization failed!");
            System.exit(1);
        }
    }

    public static String esIp() {
        return app.getElasticsearch().getIp();
    }

    public static int esPort() {
        return app.getElasticsearch().getPort();
    }

    public static String esUsername() {
        return app.getElasticsearch().getUsername();
    }

    public static String esPassword() {
        return app.getElasticsearch().getPassword();
    }

    public static String gfIp() {
        return app.getGrafana().getIp();
    }

    public static int gfPort() {
        return app.getGrafana().getPort();
    }

    public static String gfUsername() {
        return app.getGrafana().getUsername();
    }

    public static String gfPassword() {
        return app.getGrafana().getPassword();
    }

    public static String httpBaseUrl() {
        return "https://" + sernesId() + ".backend.sernes.cn/api";
    }

    public static String wsBaseUrl() {
        return "wss://" + sernesId() + ".backend.sernes.cn";
    }

    public static String sernesId() {
        return app.getTopology().getSernesId();
    }

    public static long wsTimeoutS() {
        return app.getTopology().getWs().getTimeoutS();
    }

    public static int wsMaxRetries() {
        return app.getTopology().getWs().getMaxRetries();
    }

    public static long wsRetryWaitMs() {
        return app.getTopology().getWs().getRetryWaitMs();
    }

    public static int wsMaxConnections() {
        return app.getTopology().getWs().getMaxConnections();
    }

    public static String detectionServiceUrl() {
        return app.getAnomalyDetect().getUrl();
    }

    public static String detectUrl() {
        return detectionServiceUrl() + "/detect";
    }

    public static int detectWindow() {
        return app.getAnomalyDetect().getDetectWindow();
    }

    public static int combineSeconds() {
        return app.getAnomalyDetect().getCombineSeconds();
    }

    public static int periodInterval() {
        return app.getAnomalyDetect().getPeriodInterval();
    }

    public static String basePath() {
        return app.getBaseFilePath();
    }

    public static String zipPath() {
        return basePath() + "zips/";
    }

    public static String unzipPath() {
        return basePath() + "unzips/";
    }

    public static String topologyPath() {
        return basePath() + "topology/";
    }

    public static String containerFilePath() {
        return "/usr/routebox/files";
    }

    public static String dataPath() {
        return basePath() + "data/";
    }

    public static String trainPath() {
        return dataPath() + "train/";
    }

    public static String trainCsvPath() {
        return trainPath() + "csv/";
    }

    public static String evalPath() {
        return dataPath() + "eval/";
    }

    public static String evalCsvPath() {
        return evalPath() + "csv/";
    }
}