package com.routebox.manager.anomaly;

import com.routebox.entity.index.PingLog;
import com.routebox.util.TimeUtil;
import lombok.Data;

import java.util.*;

/**
 * @Description Network Probe Anomaly Constructor
 * @Date 2025/11/24
**/
public class PingAnomalyParser extends AnomalyParser {
    // Threshold Parameters
    private static final float BASE_THRESHOLD = 200.0f;
    private static final float HIGH_DELAY_THRESHOLD = 2000.0f;
    private static final float LOW_DELAY_FLUCTUATION = 5.0f;
    private static final float HIGH_DELAY_FLUCTUATION = 2.0f;
    private static final long TIMEOUT = 10000;
    private static final float MIN_RECORD = 1.0f;

    public static String parse(Map<Integer, List<PingLog>> pingLogMap) {
        // Initialize Anomaly Categories
        AnomalyCategory timeoutCategory = new AnomalyCategory("[OVERTIME PING]");
        AnomalyCategory highDelayCategory = new AnomalyCategory("[HIGH-DELAY PING]");
        AnomalyCategory fluctuationCategory = new AnomalyCategory("[FLUCTUATION PING]");

        Map<Integer, Map<Integer, List<DelayDataPoint>>> tempDataMap = new HashMap<>();
        pingLogMap.keySet().forEach(nodeId -> tempDataMap.put(nodeId, new HashMap<>()));

        // Reorganize data by (nodeId -> pingNodeId)
        pingLogMap.forEach((nodeId, logs) ->
                logs.forEach(log ->
                        tempDataMap.get(nodeId)
                                .computeIfAbsent(log.getPingNodeId(), k -> new ArrayList<>())
                                .add(new DelayDataPoint(log.getTimestamp(), log.getDelayMs()))
                )
        );

        // Parse each (source -> target) pair
        tempDataMap.forEach((sourceNodeId, targetMap) ->
                targetMap.forEach((targetNodeId, dataPoints) -> {
                    if (dataPoints.size() <= 1) return;

                    dataPoints.sort(Comparator.comparingLong(DelayDataPoint::getTimestamp));
                    parseOnePathData(targetNodeId, dataPoints, timeoutCategory, highDelayCategory, fluctuationCategory);
                })
        );

        return parse(timeoutCategory, highDelayCategory, fluctuationCategory);
    }

    /**
     * @Description Parse single path data
     * @Date 2025/7/26
     **/
    private static void parseOnePathData(int targetNodeId, List<DelayDataPoint> dataPoints, AnomalyCategory timeoutCategory, AnomalyCategory highDelayCategory, AnomalyCategory fluctuationCategory) {
        // Check single delay value
        for (DelayDataPoint point : dataPoints) {
            float delay = point.getDelayMs();
            if (delay == TIMEOUT) {
                timeoutCategory.addRecord(targetNodeId, point.getTimestamp());
            } else if (delay > HIGH_DELAY_THRESHOLD) {
                highDelayCategory.addRecord(targetNodeId, point.getTimestamp());
            }
        }

        // Check adjacent delay value fluctuation
        for (int i = 0; i < dataPoints.size() - 1; i++) {
            DelayDataPoint current = dataPoints.get(i);
            DelayDataPoint next = dataPoints.get(i + 1);
            float currentDelay = current.getDelayMs();
            float nextDelay = next.getDelayMs();

            // Skip invalid data points
            if (currentDelay == TIMEOUT || nextDelay == TIMEOUT || currentDelay < MIN_RECORD || nextDelay < MIN_RECORD)
                continue;

            // Check delay value fluctuation
            float threshold = (currentDelay > BASE_THRESHOLD || nextDelay > BASE_THRESHOLD) ? HIGH_DELAY_FLUCTUATION : LOW_DELAY_FLUCTUATION;
            float ratio = Math.max(currentDelay, nextDelay) / Math.min(currentDelay, nextDelay);

            if (ratio > threshold) {
                fluctuationCategory.addRecord(targetNodeId, current.getTimestamp());
            }
        }
    }

    /**
     * @Description Merge anomaly categories into a single string
     * @Date 2025/7/26
     **/
    private static String parse(AnomalyCategory timeoutCategory, AnomalyCategory highDelayCategory, AnomalyCategory fluctuationCategory) {
        StringBuilder sb = new StringBuilder();

        sb.append("Display Format (Ping Targets: Abnormal Records Count)\n");

        // Timeout Anomalies
        if (timeoutCategory.totalRecords > 0) {
            sb.append(buildCategoryOutput(timeoutCategory));
        }
        // High-Delay Anomalies
        if (highDelayCategory.totalRecords > 0) {
            sb.append(buildCategoryOutput(highDelayCategory));
        }
        // Fluctuation Anomalies
        if (fluctuationCategory.totalRecords > 0) {
            sb.append(buildCategoryOutput(fluctuationCategory));
        }

        // No Anomalies Situation
        if (sb.isEmpty()) return null;

        sb.insert(0, "Display Format (Ping Targets: Abnormal Records Count)\n");
        return sb.toString();
    }

    private static String buildCategoryOutput(AnomalyCategory category) {
        StringBuilder sb4LLM = new StringBuilder();

        // Category Title and Basic Statistics
        sb4LLM.append(category.type)
                .append(" Total: ").append(category.totalRecords)
                .append("; Timeline: ")
                .append(TimeUtil.timestamp10ToTimeStr(category.minTime))
                .append(" - ")
                .append(TimeUtil.timestamp10ToTimeStr(category.maxTime))
                .append("\n");

        // Convert [targetId -> count] to [count -> targetId list]
        Map<Integer, List<Integer>> countToTargetsMap = new HashMap<>();
        category.targetCounts.forEach((targetId, count) -> countToTargetsMap.computeIfAbsent(count, k -> new ArrayList<>()).add(targetId));

        // Sort counts in descending order
        List<Integer> sortedCounts = new ArrayList<>(countToTargetsMap.keySet());
        sortedCounts.sort(Comparator.reverseOrder());

        // Build output entries (count descending, node ascending)
        List<String> entryList = new ArrayList<>();
        for (int count : sortedCounts) {
            List<Integer> targets = countToTargetsMap.get(count);
            targets.sort(Comparator.naturalOrder());

            String targetStr = targets.size() == 1
                    ? targets.getFirst().toString()
                    : "[" + String.join(",", targets.stream().map(String::valueOf).toList()) + "]";

            entryList.add(targetStr + ": " + count);
        }

        sb4LLM.append(String.join("\n", entryList)).append("\n");

        return sb4LLM.toString();
    }

    /**
     * @Description Anomaly Category Container
     * @Date 2025/11/12
     **/
    private static class AnomalyCategory {
        final String type;
        int totalRecords;
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        final Map<Integer, Integer> targetCounts = new HashMap<>();

        public AnomalyCategory(String type) {
            this.type = type;
        }

        public void addRecord(int targetNodeId, long timestamp) {
            targetCounts.put(targetNodeId, targetCounts.getOrDefault(targetNodeId, 0) + 1);
            totalRecords++;

            if (timestamp < minTime) {
                minTime = timestamp;
            }
            if (timestamp > maxTime) {
                maxTime = timestamp;
            }
        }
    }

    /**
     * @Description Delay Data Point
     * @Date 2025/7/26
     **/
    @Data
    static class DelayDataPoint {
        long timestamp;
        float delayMs;

        public DelayDataPoint(long timestamp, float delayMs) {
            this.timestamp = timestamp;
            this.delayMs = delayMs;
        }
    }
}