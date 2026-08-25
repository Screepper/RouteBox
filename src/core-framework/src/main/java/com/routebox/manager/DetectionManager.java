package com.routebox.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.routebox.common.constants.ConfConstants;
import com.routebox.common.enums.DetectStage;
import com.routebox.common.response.WsR;
import com.routebox.entity.ad.*;
import com.routebox.entity.index.*;
import com.routebox.entity.topology.NodeVethIpInfo;
import com.routebox.entity.topology.TopologyInfo;
import com.routebox.manager.anomaly.*;
import com.routebox.service.EsService;
import com.routebox.util.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @Description Detection manager
 * @Date 2025/11/11
**/

@Component
@Slf4j
public class DetectionManager {
    private final DatasetManager datasetManager;
    private final EsService esService;
    private static final long DETECT_DELAY = 40;

    public DetectionManager(DatasetManager datasetManager, EsService esService) {
        this.datasetManager = datasetManager;
        this.esService = esService;
    }

    /**
     * @Description Real-time detection
     * @Date 2025/7/27
     **/
    @Scheduled(cron = "${scheduler.real-time-detect-cron}")
    public void realTimeDetect() {
        long startTime = TimeUtil.getNowTimestamp10() - ConfConstants.detectWindow() - DETECT_DELAY;
        long earliestNormalTime = TopologyManager.earliestNormalTimestamp();
        String currentRoundDesc = "Current Detection Window: [" + TimeUtil.timestamp10ToTimeStr(startTime) + " - " + TimeUtil.timestamp10ToTimeStr(startTime + ConfConstants.detectWindow()) + "]";

        // If dont have enough normal data, skip detection
        if (startTime < earliestNormalTime) {
            String enoughDataTimeStr = TimeUtil.timestamp10ToDateTimeStr(earliestNormalTime + ConfConstants.detectWindow());
            String enoughDataTimeDesc = currentRoundDesc + "\n(At least before " + enoughDataTimeStr + ", normal logs are not enough for anomaly detection)";

            WsMesUtil.start(enoughDataTimeDesc);
            WsMesUtil.stop();

            log.info("Skip: At least before {}, normal logs are not enough for anomaly detection", enoughDataTimeStr);
            return;
        }

        WsMesUtil.start(currentRoundDesc);

        try {
            detect(startTime, "SBRPT");
        } catch (Exception e) {
            log.error("Current real-time detection failed: {}", e.getMessage());
        }
    }

    /**
     * @Description Detect and save result
     * @Date 2025/11/24
     **/
    private void detect(Long startTime, String ds) {
        long endTime = startTime + ConfConstants.detectWindow() - 1;
        List<Integer> allNodeIds = TopologyManager.topologyInfo.getNodeIds();
        List<Integer> asNodeIds = TopologyManager.topologyInfo.getAsNodeIds();
        DetectResultLog detectResultLog = new DetectResultLog();
        detectResultLog.setDataSource(ds);

        log.info("Start processing SystemNetwork logs...");
        WsMesUtil.push(new WsR(DetectStage.PROCESS_DATA, "Pulling and processing SystemNetwork logs..."));
        // Pull and process current window logs
        Map<Integer, SystemNetworkLog[]> currentCombinedMap = datasetManager.pullAndCombineRangeSNLogs(
                allNodeIds,
                startTime,
                endTime
        );
        // Pull and process recent window logs
        Map<Integer, SystemNetworkLog[]> recentCombinedMap = datasetManager.pullAndCombineRangeSNLogs(
                allNodeIds,
                startTime - ConfConstants.detectWindow() * 2L,
                startTime - 1,
                true
        );
        // Pull and process period window logs
        long periodStartTime = startTime - ConfConstants.periodInterval();
        long periodEndTime = periodStartTime + ConfConstants.detectWindow() - 1;
        Map<Integer, SystemNetworkLog[]> periodCombinedMap = datasetManager.pullAndCombineRangeSNLogs(
                allNodeIds,
                periodStartTime,
                periodEndTime,
                true
        );
        WsMesUtil.push(new WsR(DetectStage.ANOMALY_DETECT, "All SystemNetwork logs processed, start Detecting SystemNetwork Anomalies..."));
        // Detect SystemNetwork anomalies
        List<AnomalyInfo> systemNetworkAnomalies = HttpUtil.typeRefPost(
                ConfConstants.detectUrl(),
                new DetectRequest(currentCombinedMap, recentCombinedMap, periodCombinedMap),
                new TypeReference<>() {
                }
        );
        // Early return if no anomalies
        if (systemNetworkAnomalies == null || systemNetworkAnomalies.isEmpty()) {
            log.info("No anomalies!");
            detectResultLog.setIsAnomaly(false);
            EsIndexUtil.fillIndexBase(detectResultLog);
            esService.save(detectResultLog);
            WsMesUtil.stop();

            return;
        }

        // Observed anomalous node ids
        List<Integer> anomalyNodeIds = systemNetworkAnomalies.stream().map(AnomalyInfo::getNodeId).sorted().toList();
        log.info("Anomaly detection completed, {} anomalies total", systemNetworkAnomalies.size());

        // Fill other anomaly info
        detectResultLog.setIsAnomaly(true);
        // Main topology info
        detectResultLog.setTopologyInfo(buildTopologyContext(startTime, endTime, anomalyNodeIds));
        // Parsed SystemNetwork anomalies
        detectResultLog.setSystemNetworkAnomaly(SystemNetworkAnomalyParser.postParse(systemNetworkAnomalies));
        // Parse BGP anomalies
        if (ds.contains("B")) {
            log.info("Processing BGP Logs...");
            WsMesUtil.push(new WsR(DetectStage.PROCESS_DATA, "Pulling and processing BGP logs..."));
            Map<Integer, List<BgpLog>> sourceMap = datasetManager.pullRangeLogs(asNodeIds, startTime, endTime, BgpLog.class);
            WsMesUtil.push(new WsR(DetectStage.ANOMALY_DETECT, "All BGP logs processed, start Detecting BGP Anomalies..."));
            String parsed = BgpAnomalyParser.parse(sourceMap);
            detectResultLog.setBgpAnomaly(parsed);
            WsMesUtil.push(new WsR(DetectStage.DETECT_RESULT, parsed));
        }
        // Parse Router anomalies
        if (ds.contains("R")) {
            log.info("Processing Router Logs...");
            WsMesUtil.push(new WsR(DetectStage.PROCESS_DATA, "Pulling and processing Router logs..."));
            Map<Integer, List<RouterLog>> sourceMap = datasetManager.pullRangeLogs(asNodeIds, startTime, endTime, RouterLog.class);
            WsMesUtil.push(new WsR(DetectStage.ANOMALY_DETECT, "All Router logs processed, start Detecting Router Anomalies..."));
            String parsed = RouterAnomalyParser.parse(sourceMap);
            detectResultLog.setRouterAnomaly(parsed);
            WsMesUtil.push(new WsR(DetectStage.DETECT_RESULT, parsed));
        }
        // Parse Ping anomalies
        if (ds.contains("P")) {
            log.info("Processing Ping Logs...");
            WsMesUtil.push(new WsR(DetectStage.PROCESS_DATA, "Pulling and processing Ping logs..."));
            Map<Integer, List<PingLog>> sourceMap = datasetManager.pullRangeLogs(allNodeIds, startTime, endTime, PingLog.class);
            WsMesUtil.push(new WsR(DetectStage.ANOMALY_DETECT, "All Ping logs processed, start Detecting Ping Anomalies..."));
            String parsed = PingAnomalyParser.parse(sourceMap);
            detectResultLog.setPingAnomaly(parsed);
            WsMesUtil.push(new WsR(DetectStage.DETECT_RESULT, parsed));
        }
        // Parse Topology anomalies
        if (ds.contains("T")) {
            log.info("Processing Topology Logs...");
            WsMesUtil.push(new WsR(DetectStage.PROCESS_DATA, "Pulling and processing Topology logs..."));
            List<TopologyLog> sourceLogs = datasetManager.pullRangeLogs(startTime, endTime, TopologyLog.class);
            WsMesUtil.push(new WsR(DetectStage.ANOMALY_DETECT, "All Topology logs processed, start Detecting Topology Anomalies..."));
            String parsed = TopologyAnomalyParser.parse(sourceLogs);
            detectResultLog.setTopologyAnomaly(parsed);
            WsMesUtil.push(new WsR(DetectStage.DETECT_RESULT, parsed));
        }

        // Push to ES
        EsIndexUtil.fillIndexBase(detectResultLog);
        esService.save(detectResultLog);

        log.info("Anomaly report saved successfully!\n");
        WsMesUtil.stop();
    }

    /**
     * @Description Generate basic topology information
     * @Date 2025/7/26
     **/
    private String buildTopologyContext(long startTime, long endTime, List<Integer> anomalyNodeIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("===Topology Information===\n");

        // Nodes info
        TopologyInfo tpInfo = TopologyManager.topologyInfo;
        sb.append("Node count: ")
                .append(tpInfo.getNodeIds().size())
                .append(" (AS: ")
                .append(tpInfo.getAsNodeIds().size())
                .append(", Host: ")
                .append(tpInfo.getHostNodeIds().size())
                .append(")\n");

        // Adjacency info
        sb.append("Import adjacency: \n");
        HashMap<Integer, String> id2TypeMap = tpInfo.getId2TypeMap();
        HashMap<Integer, Map<Integer, NodeVethIpInfo>> vethIpInfo = tpInfo.getNodeVethIpInfo();
        tpInfo.getAdjacencyList().forEach((nodeId, neighbors) -> {
            if (anomalyNodeIds.contains(nodeId)) {
                ArrayList<Integer> sortedList = new ArrayList<>(neighbors);
                sortedList.sort(Comparator.comparingInt(Integer::intValue));
                sb.append(id2TypeMap.get(nodeId)).append(nodeId).append(": [");

                for (int adjacencyNodeId : sortedList) {
                    sb.append(vethIpInfo.get(nodeId).get(adjacencyNodeId).getFromVeth())
                            .append("->")
                            .append(adjacencyNodeId)
                            .append(", ");
                }

                // Delete the last comma and space
                sb.delete(sb.length() - 2, sb.length());
                sb.append("]\n");
            }
        });

        // Time window
        sb.append("Time window: ")
                .append(TimeUtil.timestamp10ToDateTimeStr(startTime))
                .append(" - ")
                .append(TimeUtil.timestamp10ToDateTimeStr(endTime))
                .append("\n");

        return sb.toString();
    }
}