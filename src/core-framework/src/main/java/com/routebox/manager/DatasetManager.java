package com.routebox.manager;

import com.routebox.common.constants.ConfConstants;
import com.routebox.entity.index.*;
import com.routebox.entity.index.auxiliary.NicInfo;
import com.routebox.entity.index.auxiliary.SystemNetworkWithLabel;
import com.routebox.service.EsService;
import com.routebox.util.TimeUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * @Description Data processing module
 * @Date 2025/7/27
**/
@Component
@Slf4j
public class DatasetManager {
    private final EsService esService;

    private static final long RETRY_INTERVAL_MS = 500; // Log pull retry interval
    private static final int BATCH_NODES = 50; // Batch node number
    private static final long EARLIEST_START_TIME = 1756704871; // Your Earliest Log Record Timestamp
    private static final Map<long[], List<Integer>> ANOMALY_TIME_NODES = new HashMap<>() {
    }; // Manually confirmed abnormal time periods and nodes

    public DatasetManager(EsService esService) {
        this.esService = esService;
    }

    /**
     * @Description Generate training dataset
     * @Date 2025/6/21
     **/
    @Deprecated
    public void generateTrainDataset() {
        long startTime = 1760090400;
        long endTime = startTime + ConfConstants.detectWindow() - 1;
        long targetEndTime = 1760754600;
        List<Integer> nodeIds = TopologyManager.topologyInfo.getNodeIds();

        Path trainFolder = Paths.get(ConfConstants.trainCsvPath());
        if (!Files.exists(trainFolder)) {
            try {
                Files.createDirectories(trainFolder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        while (endTime <= targetEndTime) {
            log.info("Current time range: {} - {}", TimeUtil.timestamp10ToDateTimeStr(startTime), TimeUtil.timestamp10ToDateTimeStr(endTime));

            // Pull, preprocess, aggregate source logs
            Map<Integer, SystemNetworkLog[]> aggregatedLogsMap = pullAndCombineRangeSNLogs(nodeIds, startTime, startTime + ConfConstants.detectWindow() - 1, true);
            // Save to CSV
            log2TrainCsv(aggregatedLogsMap);
            // Move to next window
            startTime += ConfConstants.detectWindow();
            endTime += ConfConstants.detectWindow();
        }

        log.info("Training dataset generation completed");
    }

    /**
     * @Description Generate evaluation dataset (Only attack event period 30min)
     * @Date 2025/9/29
     **/
    @Deprecated
    public void generateEvalDataset() {
        List<Integer> nodeIds = IntStream.range(1, 9).boxed().toList();
        Path evalFolder = Paths.get(ConfConstants.evalCsvPath());
        if (!Files.exists(evalFolder)) {
            try {
                Files.createDirectories(evalFolder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        List<AttackLog> attackLogs = esService.searchByOrder(AttackLog.class, true);

        for (AttackLog attackLog : attackLogs) {
            long start = attackLog.getTimestamp();

            long startTime = start - 600;
            long endTime = startTime + ConfConstants.detectWindow() - 1;

            log.info("Current time range: {} - {}", TimeUtil.timestamp10ToDateTimeStr(startTime), TimeUtil.timestamp10ToDateTimeStr(endTime));

            // Pull and preprocess logs with labels
            Map<Integer, SystemNetworkWithLabel[]> aggregatedLogsMap = pullAndCombineLabeledLogs(nodeIds, startTime, endTime);
            // Save to CSV
            saveLabeledLogsToCsv(aggregatedLogsMap);
        }

        log.info("Evaluation dataset generation completed");
    }

    /**
     * @Description Recursively pull normal period logs
     * @Date 2025/9/4
     **/
    private NormalLogs tryToPullNormalLogs(int nodeId, long startTime, long endTime) {
        long timeInterval = endTime - startTime;

        // Return null if startTime is before the earliest available time
        if (startTime < EARLIEST_START_TIME) {
            log.warn("Node {} has no available normal data before querying the earliest time {}", nodeId, TimeUtil.timestamp10ToDateTimeStr(EARLIEST_START_TIME));
            return null;
        }

        // If attack event is found in the time range, try to pull the previous period logs
        if (esService.checkAttackInTimeRange(startTime, endTime)) {
            startTime = startTime - ConfConstants.periodInterval();
            endTime = startTime + timeInterval;

            return tryToPullNormalLogs(nodeId, startTime, endTime);
        } else {
            List<SystemNetworkLog> logs = searchByNodeIdAndTimeWithRetry(nodeId, startTime, endTime, SystemNetworkLog.class);
            NormalLogs normalLogs = new NormalLogs();
            normalLogs.finalStartTime = startTime;
            normalLogs.finalEndTime = endTime;
            normalLogs.logs = logs;

            return normalLogs;
        }
    }

    /**
     * @Description Pull and aggregate logs
     * @Date 2025/9/5
     **/
    public <T extends IndexBase> List<T> pullRangeLogs(long startTime, long endTime, Class<T> clazz) {
        int retryTimes = 3;
        List<T> logs;
        String startTimeStr = TimeUtil.timestamp10ToDateTimeStr(startTime);
        String endTimeStr = TimeUtil.timestamp10ToDateTimeStr(endTime);

        while (retryTimes > 0) {
            logs = esService.searchByTimeRange(startTime, endTime, clazz);

            if (logs != null) {
                if (logs.isEmpty()) {
                    log.warn("Pull logs from [{} - {}] return empty list", startTimeStr, endTimeStr);
                    return null;
                }
                return logs.stream().sorted(Comparator.comparingLong(IndexBase::getTimestamp)).toList();
            }

            retryTimes--;
            if (retryTimes > 0) {
                try {
                    log.warn("Pull logs from [{} - {}] exception, remaining retry times: {}", startTimeStr, endTimeStr, retryTimes);
                    TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Pull logs from [{} - {}] retry wait interrupted, terminate retry process", startTimeStr, endTimeStr, e);
                    return null;
                }
            }
        }
        log.error("Pull logs from [{} - {}] exception, retry null list", startTimeStr, endTimeStr);

        return null;
    }

    /**
     * @Description Recursively pull logs with retry mechanism
     * @Date 2025/9/9
     **/
    private <T extends IndexBase> List<T> searchByNodeIdAndTimeWithRetry(Integer nodeId, Long startTime, Long endTime, Class<T> clazz) {
        int retryTimes = 3;
        List<T> logs;
        String startTimeStr = TimeUtil.timestamp10ToDateTimeStr(startTime);
        String endTimeStr = TimeUtil.timestamp10ToDateTimeStr(endTime);

        while (retryTimes > 0) {
            logs = esService.searchByNodeIdAndTimeRange(nodeId, startTime, endTime, clazz);

            if (logs != null) {
                if (logs.isEmpty()) {
                    log.warn("Node {} pull logs from [{} - {}] return empty list", nodeId, startTimeStr, endTimeStr);
                    return null;
                }
                return logs.stream().sorted(Comparator.comparingLong(IndexBase::getTimestamp)).toList();
            }

            retryTimes--;
            if (retryTimes > 0) {
                try {
                    log.warn("Node {} pull logs from [{} - {}] exception, remaining retry times: {}", nodeId, startTimeStr, endTimeStr, retryTimes);
                    TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Node {} pull logs from [{} - {}] retry wait interrupted, terminate retry process", nodeId, startTimeStr, endTimeStr, e);
                    return null;
                }
            }
        }
        log.error("Node {} pull logs from [{} - {}] exception, retry null list", nodeId, startTimeStr, endTimeStr);

        return null;
    }

    /**
     * @Description Pull and aggregate logs
     * @Date 2025/9/5
     **/
    public <T extends IndexBase> Map<Integer, List<T>> pullRangeLogs(List<Integer> nodeIds, long startTime, long endTime, Class<T> clazz) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Pull raw logs
            Map<Integer, List<T>> sourceMap = new ConcurrentHashMap<>();
            List<Future<List<T>>> pullFutures = new ArrayList<>();

            for (int i = 0; i < nodeIds.size(); i += BATCH_NODES) {
                int end = Math.min(i + BATCH_NODES, nodeIds.size());
                List<Integer> subNodeIds = nodeIds.subList(i, end);
                pullFutures.clear();
                subNodeIds.forEach(nodeId -> pullFutures.add(executor.submit(() -> searchByNodeIdAndTimeWithRetry(nodeId, startTime, endTime, clazz))));
                processConcurrentResults(pullFutures, sourceMap, logs -> logs.getFirst().getNodeId());
            }

            Map<Integer, List<T>> sortedMap = new HashMap<>();
            sourceMap.forEach((nodeId, logs) -> sortedMap.put(nodeId, logs.stream().sorted(Comparator.comparingLong(IndexBase::getTimestamp)).toList()));

            return sortedMap;
        }
    }

    /**
     * @Description Recursively pull and process labeled logs
     * @Date 2025/9/29
     **/
    private Map<Integer, SystemNetworkWithLabel[]> pullAndCombineLabeledLogs(List<Integer> nodeIds, long startTime, long endTime) {
        // Pull raw logs
        Map<Integer, List<SystemNetworkLog>> rawLogsMap = pullRangeLogs(nodeIds, startTime, endTime, SystemNetworkLog.class);
        Map<Integer, SystemNetworkWithLabel[]> labeledLogsMap = new HashMap<>();

        rawLogsMap.forEach((nodeId, rawLogs) -> {
            // Preprocess logs
            SystemNetworkLog[] preprocessedLogs = preprocessLogs(startTime, endTime, nodeId, rawLogs);
            // Set anomaly labels based on time ranges
            SystemNetworkWithLabel[] labeledLogs = setAnomalyLabels(preprocessedLogs, nodeId, startTime, endTime);
            labeledLogsMap.put(nodeId, labeledLogs);
        });

        // Aggregate logs
        Map<Integer, SystemNetworkWithLabel[]> aggregatedLogsMap = new HashMap<>();
        labeledLogsMap.forEach((nodeId, labeledLogs) -> aggregatedLogsMap.put(nodeId, combineLabeledLogs(labeledLogs)));

        return aggregatedLogsMap;
    }

    /**
     * @Description Pull and aggregate logs
     * @Date 2025/9/5
     **/
    public Map<Integer, SystemNetworkLog[]> pullAndCombineRangeSNLogs(List<Integer> nodeIds, long startTime, long endTime, boolean... mustNormal) {
        boolean pullNormal = mustNormal.length > 0 && mustNormal[0];
        final Map<Integer, long[]> normalLogTimeLabels = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Pull raw logs
            Map<Integer, List<SystemNetworkLog>> sourceMap = new ConcurrentHashMap<>();

            // Submit batch tasks
            if (pullNormal) {
                List<Future<NormalLogs>> pullFutures = new ArrayList<>();
                for (int i = 0; i < nodeIds.size(); i += BATCH_NODES) {
                    int end = Math.min(i + BATCH_NODES, nodeIds.size());
                    List<Integer> subNodeIds = nodeIds.subList(i, end);
                    pullFutures.clear();
                    subNodeIds.forEach(nodeId -> pullFutures.add(executor.submit(() -> tryToPullNormalLogs(nodeId, startTime, endTime))));
                    for (Future<NormalLogs> future : pullFutures) {
                        try {
                            NormalLogs result = future.get();

                            if (result == null) continue;

                            List<SystemNetworkLog> logs = result.getLogs();
                            Integer nodeId = logs.getFirst().getNodeId();
                            sourceMap.put(nodeId, logs);
                            normalLogTimeLabels.put(nodeId, new long[]{result.getFinalStartTime(), result.getFinalEndTime()});
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("Concurrent task interrupted", e);
                        } catch (ExecutionException e) {
                            log.error("Concurrent task execution failed", e.getCause());
                        }
                    }
                }
            } else {
                List<Future<List<SystemNetworkLog>>> pullFutures = new ArrayList<>();

                for (int i = 0; i < nodeIds.size(); i += BATCH_NODES) {
                    int end = Math.min(i + BATCH_NODES, nodeIds.size());
                    List<Integer> subNodeIds = nodeIds.subList(i, end);
                    pullFutures.clear();
                    subNodeIds.forEach(nodeId -> pullFutures.add(executor.submit(() -> searchByNodeIdAndTimeWithRetry(nodeId, startTime, endTime, SystemNetworkLog.class))));
                    processConcurrentResults(pullFutures, sourceMap, logs -> logs.getFirst().getNodeId());
                }
            }

            // Fill missing nodes
            nodeIds.forEach(nodeId -> sourceMap.putIfAbsent(nodeId, new ArrayList<>()));
            // Preprocess logs
            Map<Integer, SystemNetworkLog[]> cleanedMap = new ConcurrentHashMap<>();
            List<Future<SystemNetworkLog[]>> cleanFutures = new ArrayList<>();
            // Submit preprocess tasks
            if (pullNormal) {
                sourceMap.forEach((nodeId, logs) -> cleanFutures.add(executor.submit(() -> preprocessLogs(
                        normalLogTimeLabels.get(nodeId)[0],
                        normalLogTimeLabels.get(nodeId)[1],
                        nodeId, logs
                ))));
            } else {
                sourceMap.forEach((nodeId, logs) -> cleanFutures.add(executor.submit(() -> preprocessLogs(startTime, endTime, nodeId, logs))));
            }
            // Process preprocess results
            processConcurrentResults(cleanFutures, cleanedMap, cleanedLogs -> cleanedLogs[0].getNodeId());
            // Aggregate logs
            Map<Integer, SystemNetworkLog[]> currentCombinedMap = new ConcurrentHashMap<>();
            List<Future<SystemNetworkLog[]>> currentCombineFutures = new ArrayList<>();
            // Submit aggregate tasks
            cleanedMap.forEach((nodeId, logs) -> currentCombineFutures.add(executor.submit(() -> combineLogs(logs))));
            // Process aggregate results
            processConcurrentResults(currentCombineFutures, currentCombinedMap, combinedLogs -> combinedLogs[0].getNodeId());

            return currentCombinedMap;
        }
    }

    /**
     * @Description Set anomaly labels based on time ranges defined in ANOMALY_TIME_NODES
     * @Date 2025/9/29
     **/
    private SystemNetworkWithLabel[] setAnomalyLabels(SystemNetworkLog[] logs, int nodeId, long windowStartTime, long windowEndTime) {
        SystemNetworkWithLabel[] labeledLogs = new SystemNetworkWithLabel[logs.length];

        // Create labeled logs with default label 0
        for (int i = 0; i < logs.length; i++) {
            labeledLogs[i] = new SystemNetworkWithLabel();
            labeledLogs[i].setLabel(0);
            BeanUtils.copyProperties(logs[i], labeledLogs[i]);
        }

        // Check if the current node is in any anomaly event
        for (Map.Entry<long[], List<Integer>> entry : ANOMALY_TIME_NODES.entrySet()) {
            long[] anomalyTimeRange = entry.getKey();
            long anomalyStart = anomalyTimeRange[0];
            long anomalyEnd = anomalyTimeRange[1];
            List<Integer> anomalyNodes = entry.getValue();
            // Check if the current node is in the anomaly nodes list and the time range has intersection
            if (anomalyNodes.contains(nodeId) && !(windowEndTime < anomalyStart || windowStartTime > anomalyEnd)) {
                // Set label 1 for logs within the anomaly time range
                for (SystemNetworkWithLabel log : labeledLogs) {
                    long logTime = log.getTimestamp();

                    if (logTime >= anomalyStart && logTime <= anomalyEnd) {
                        log.setLabel(1);
                    }
                }
            }
        }

        return labeledLogs;
    }

    /**
     * @Description Aggregate logs within 30 seconds
     * @Date 2025/9/3
     **/
    private SystemNetworkLog[] combineLogs(SystemNetworkLog[] cleanedLogs) {
        // Group logs by timestamp within 30 seconds
        Map<Long, SystemNetworkLog[]> logGroups = new LinkedHashMap<>();
        int combinedNum = cleanedLogs.length / ConfConstants.combineSeconds();

        for (int i = 0; i < combinedNum; i++) {
            int startIndex = i * ConfConstants.combineSeconds();
            int endIndex = startIndex + ConfConstants.combineSeconds();
            logGroups.put(cleanedLogs[startIndex + ConfConstants.combineSeconds() / 2].getTimestamp(), Arrays.copyOfRange(cleanedLogs, startIndex, endIndex));
        }

        // Traverse groups, aggregate logs (sum + avg)
        SystemNetworkLog[] combinedLogs = new SystemNetworkLog[combinedNum];
        int i = 0;
        for (Map.Entry<Long, SystemNetworkLog[]> entry : logGroups.entrySet()) {
            Long timestamp = entry.getKey();
            SystemNetworkLog[] logs = entry.getValue();
            List<NicInfo> nicsTemplate = TopologyManager.getNicsTemplate(logs[0].getNodeId());

            // Initialize combined log
            SystemNetworkLog combinedLog = new SystemNetworkLog();
            combinedLog.setTimestamp(timestamp);
            combinedLog.setNodeId(logs[0].getNodeId());
            combinedLog.setNics(nicsTemplate);

            // Initialize sum fields
            int totalTps = 0;
            float totalIoWriteKb = 0f;
            int totalReceivePacketsRate = 0;
            int totalTransmitPacketsRate = 0;
            float totalReceiveKbRate = 0f;
            float totalTransmitKbRate = 0f;

            Map<String, NicAccumulator> nicAccumulators = new HashMap<>();
            for (NicInfo nic : combinedLog.getNics()) {
                nicAccumulators.put(nic.getName(), new NicAccumulator());
            }

            // Initialize average fields
            float totalCpuUseRatio = 0f;
            float totalSystemUseRatio = 0f;
            long totalUsedMemoryKb = 0;
            float totalMemoryUseRatio = 0f;

            // Traverse logs in the group, accumulate sum fields
            for (SystemNetworkLog log : logs) {
                totalTps += log.getTps();
                totalIoWriteKb += log.getIoWriteKb();
                totalReceivePacketsRate += log.getTotalReceivePacketsRate();
                totalTransmitPacketsRate += log.getTotalTransmitPacketsRate();
                totalReceiveKbRate += log.getTotalReceiveKbRate();
                totalTransmitKbRate += log.getTotalTransmitKbRate();

                for (NicInfo nic : log.getNics()) {
                    NicAccumulator acc = nicAccumulators.get(nic.getName());
                    acc.receivePacketsRate += nic.getReceivePacketsRate();
                    acc.transmitPacketsRate += nic.getTransmitPacketsRate();
                    acc.receiveKbRate += nic.getReceiveKbRate();
                    acc.transmitKbRate += nic.getTransmitKbRate();
                }

                totalCpuUseRatio += log.getCpuUseRatio();
                totalSystemUseRatio += log.getSystemUseRatio();
                totalUsedMemoryKb += log.getUsedMemoryKb();
                totalMemoryUseRatio += log.getMemoryUseRatio();
            }

            // Save sum fields results
            combinedLog.setTps(totalTps / ConfConstants.combineSeconds());
            combinedLog.setIoWriteKb(totalIoWriteKb);
            combinedLog.setTotalReceivePacketsRate(totalReceivePacketsRate);
            combinedLog.setTotalTransmitPacketsRate(totalTransmitPacketsRate);
            combinedLog.setTotalReceiveKbRate(totalReceiveKbRate);
            combinedLog.setTotalTransmitKbRate(totalTransmitKbRate);
            for (NicInfo targetNic : combinedLog.getNics()) {
                NicAccumulator acc = nicAccumulators.get(targetNic.getName());
                targetNic.setReceivePacketsRate(acc.receivePacketsRate);
                targetNic.setTransmitPacketsRate(acc.transmitPacketsRate);
                targetNic.setReceiveKbRate(acc.receiveKbRate);
                targetNic.setTransmitKbRate(acc.transmitKbRate);
            }

            // Save average fields results
            combinedLog.setCpuUseRatio(totalCpuUseRatio / ConfConstants.combineSeconds());
            combinedLog.setSystemUseRatio(totalSystemUseRatio / ConfConstants.combineSeconds());
            combinedLog.setUsedMemoryKb(totalUsedMemoryKb / ConfConstants.combineSeconds());
            combinedLog.setMemoryUseRatio(totalMemoryUseRatio / ConfConstants.combineSeconds());
            combinedLogs[i++] = combinedLog;
        }
        return combinedLogs;
    }

    /**
     * @Description Aggregate labeled logs within 30 seconds
     * @Date 2025/9/29
     **/
    private SystemNetworkWithLabel[] combineLabeledLogs(SystemNetworkWithLabel[] labeledLogs) {
        // Group logs by timestamp
        Map<Long, SystemNetworkWithLabel[]> logGroups = new LinkedHashMap<>();
        int combinedNum = labeledLogs.length / ConfConstants.combineSeconds();

        for (int i = 0; i < combinedNum; i++) {
            int startIndex = i * ConfConstants.combineSeconds();
            int endIndex = startIndex + ConfConstants.combineSeconds();
            logGroups.put(labeledLogs[startIndex + ConfConstants.combineSeconds() / 2].getTimestamp(), Arrays.copyOfRange(labeledLogs, startIndex, endIndex));
        }

        // Aggregate logs in each group
        SystemNetworkWithLabel[] combinedLogs = new SystemNetworkWithLabel[combinedNum];
        int i = 0;
        for (Map.Entry<Long, SystemNetworkWithLabel[]> entry : logGroups.entrySet()) {
            Long timestamp = entry.getKey();
            SystemNetworkWithLabel[] groupLogs = entry.getValue();
            List<NicInfo> nicsTemplate = TopologyManager.getNicsTemplate(groupLogs[0].getNodeId());

            // Initialize combined log
            SystemNetworkWithLabel combinedLog = new SystemNetworkWithLabel();
            combinedLog.setTimestamp(timestamp);
            combinedLog.setNodeId(groupLogs[0].getNodeId());
            combinedLog.setNics(nicsTemplate);

            // Initialize sum fields
            int totalTps = 0;
            float totalIoWriteKb = 0f;
            int totalReceivePacketsRate = 0;
            int totalTransmitPacketsRate = 0;
            float totalReceiveKbRate = 0f;
            float totalTransmitKbRate = 0f;

            Map<String, NicAccumulator> nicAccumulators = new HashMap<>();
            for (NicInfo nic : combinedLog.getNics()) {
                nicAccumulators.put(nic.getName(), new NicAccumulator());
            }

            // Initialize average fields
            float totalCpuUseRatio = 0f;
            float totalSystemUseRatio = 0f;
            long totalUsedMemoryKb = 0;
            float totalMemoryUseRatio = 0f;

            // Initialize label sum
            int totalLabel = 0;

            // Accumulate fields values in each log
            for (SystemNetworkWithLabel log : groupLogs) {
                // Accumulate original fields
                totalTps += log.getTps();
                totalIoWriteKb += log.getIoWriteKb();
                totalReceivePacketsRate += log.getTotalReceivePacketsRate();
                totalTransmitPacketsRate += log.getTotalTransmitPacketsRate();
                totalReceiveKbRate += log.getTotalReceiveKbRate();
                totalTransmitKbRate += log.getTotalTransmitKbRate();

                for (NicInfo nic : log.getNics()) {
                    NicAccumulator acc = nicAccumulators.get(nic.getName());
                    acc.receivePacketsRate += nic.getReceivePacketsRate();
                    acc.transmitPacketsRate += nic.getTransmitPacketsRate();
                    acc.receiveKbRate += nic.getReceiveKbRate();
                    acc.transmitKbRate += nic.getTransmitKbRate();
                }

                totalCpuUseRatio += log.getCpuUseRatio();
                totalSystemUseRatio += log.getSystemUseRatio();
                totalUsedMemoryKb += log.getUsedMemoryKb();
                totalMemoryUseRatio += log.getMemoryUseRatio();
                totalLabel += log.getLabel();
            }

            // Save original fields aggregate results
            combinedLog.setTps(totalTps / ConfConstants.combineSeconds());
            combinedLog.setIoWriteKb(totalIoWriteKb);
            combinedLog.setTotalReceivePacketsRate(totalReceivePacketsRate);
            combinedLog.setTotalTransmitPacketsRate(totalTransmitPacketsRate);
            combinedLog.setTotalReceiveKbRate(totalReceiveKbRate);
            combinedLog.setTotalTransmitKbRate(totalTransmitKbRate);
            for (NicInfo targetNic : combinedLog.getNics()) {
                NicAccumulator acc = nicAccumulators.get(targetNic.getName());
                targetNic.setReceivePacketsRate(acc.receivePacketsRate);
                targetNic.setTransmitPacketsRate(acc.transmitPacketsRate);
                targetNic.setReceiveKbRate(acc.receiveKbRate);
                targetNic.setTransmitKbRate(acc.transmitKbRate);
            }
            combinedLog.setCpuUseRatio(totalCpuUseRatio / ConfConstants.combineSeconds());
            combinedLog.setSystemUseRatio(totalSystemUseRatio / ConfConstants.combineSeconds());
            combinedLog.setUsedMemoryKb(totalUsedMemoryKb / ConfConstants.combineSeconds());
            combinedLog.setMemoryUseRatio(totalMemoryUseRatio / ConfConstants.combineSeconds());

            // Set aggregated label
            combinedLog.setLabel(totalLabel > 0 ? 1 : 0);
            combinedLogs[i++] = combinedLog;
        }

        return combinedLogs;
    }

    /**
     * @Description Universal concurrent task result processor
     * @Date 2025/9/4
     **/
    private <V> void processConcurrentResults(List<Future<V>> futures, Map<Integer, V> resultMap, Function<V, Integer> nodeIdExtractor) {
        for (Future<V> future : futures) {
            try {
                V result = future.get(); // Wait for the task to complete
                if (result == null) continue; // Skip null results

                int nodeId;
                try {
                    // Extract nodeId (If result is empty, here will throw exception, skip this node's result)
                    nodeId = nodeIdExtractor.apply(result);
                } catch (Exception e) {
                    continue;
                }
                resultMap.put(nodeId, result); // Store result in the map
            } catch (Exception e) {
                log.error("Concurrent task execution failed", e.getCause());
            }
        }
    }

    /**
     * @Description Preprocess time series logs, including missing value processing, interpolation, mean, fixed window, etc
     * @Date 2025/7/27
     **/
    public SystemNetworkLog[] preprocessLogs(long startTime, long endTime, int nodeId, List<SystemNetworkLog> logs) {
        // Initialize cleanedLogs array
        int intervalSeconds = (int) (endTime - startTime + 1);
        SystemNetworkLog[] cleanedLogs = new SystemNetworkLog[intervalSeconds];
        boolean[] exists = new boolean[intervalSeconds];

        // Get nics template (first valid log's nics structure)
        List<NicInfo> nicsTemplate = TopologyManager.getNicsTemplate(nodeId);
        if (logs == null || logs.isEmpty()) {
            // No valid logs, set all to 0
            for (int i = 0; i < intervalSeconds; i++) {
                long currentTime = startTime + i;
                cleanedLogs[i] = create0Log(nodeId, currentTime, nicsTemplate);
            }

            return cleanedLogs;
        }

        // Group logs by timestamp
        Map<Long, List<SystemNetworkLog>> timeMap = new HashMap<>();
        for (SystemNetworkLog log : logs) {
            timeMap.computeIfAbsent(log.getTimestamp(), k -> new ArrayList<>()).add(log);
        }

        for (int i = 0; i < intervalSeconds; i++) {
            long currentTime = startTime + i;
            List<SystemNetworkLog> logsAtTime = timeMap.get(currentTime);

            if (logsAtTime != null && !logsAtTime.isEmpty()) {
                // Combine multiple logs at the same timestamp
                cleanedLogs[i] = mergeLogsAtSameTimestamp(logsAtTime, currentTime, nodeId, nicsTemplate);
                exists[i] = true;
            } else {
                // Missing data, fill with 0
                cleanedLogs[i] = create0Log(nodeId, currentTime, nicsTemplate);
                exists[i] = false;
            }
        }

        // Handle missing values
        for (int i = 0; i < intervalSeconds; i++) {
            if (cleanedLogs[i] != null) continue;

            // Check if there is any data within the next 4 seconds
            int nextHit = -1;
            for (int j = i + 1; j <= Math.min(i + 4, 119); j++) {
                if (exists[j]) {
                    nextHit = j;
                    break;
                }
            }
            if (nextHit != -1) {
                // Try to fill missing values in [i+1, i+4]
                if (i == 0) {
                    // Header missing, fill with t(j)
                    for (int k = 0; k < nextHit; k++) {
                        cleanedLogs[k] = cloneLogWithNewTimestamp(cleanedLogs[nextHit], startTime + k);
                    }
                } else {
                    // Middle missing, fill with interpolated values
                    for (int k = i; k < nextHit; k++) {
                        cleanedLogs[k] = interpolateLogs(cleanedLogs[i - 1], cleanedLogs[nextHit], i - 1, nextHit, k, startTime + k);
                    }
                }
                // Skip processed interval
                i = nextHit - 1;
            } else {
                // If there is no data for the next 4 seconds, all logs will be set to 0 until the next real data is found
                int nextRealHit = findNextHit(exists, i + 5);
                int endIndex = (nextRealHit == -1) ? intervalSeconds : nextRealHit;

                for (int k = i; k < endIndex; k++) {
                    cleanedLogs[k] = create0Log(nodeId, startTime + k, nicsTemplate);
                }

                if (nextRealHit == -1) break;

                i = endIndex - 1;
            }
        }

        return cleanedLogs;
    }

    /**
     * @Description Convert the processed logs into CSV files for training purposes
     * @Date 2025/7/24
     **/
    public void log2TrainCsv(Map<Integer, SystemNetworkLog[]> cleanedMap) {
        cleanedMap.forEach((nodeId, logs) -> {
            Path csvPath = Paths.get(ConfConstants.trainCsvPath(), nodeId + ".csv");

            try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (SystemNetworkLog log : logs) {
                    writer.write(log2Row(log));
                }
            } catch (IOException e) {
                log.error("Failed to save processed logs for node {} to file {}", nodeId, csvPath, e);
            }
        });
    }

    /**
     * @Description Save the labeled logs to CSV files for evaluation purposes
     * @Date 2025/9/29
     **/
    private void saveLabeledLogsToCsv(Map<Integer, SystemNetworkWithLabel[]> aggregatedLogsMap) {
        aggregatedLogsMap.forEach((nodeId, logs) -> {
            Path csvPath = Paths.get(ConfConstants.evalCsvPath(), nodeId + ".csv");

            try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (SystemNetworkWithLabel log : logs) {
                    writer.write(labeledLog2Row(log));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * @Description Convert the log to a string for storage
     * @Date 2025/7/28
     **/
    private String log2Row(SystemNetworkLog log) {
        StringBuilder sb = new StringBuilder();

        // Basic fields
        sb.append(log.getNodeId()).append(",");
        sb.append(log.getTimestamp()).append(",");
        // System fields
        sb.append(log.getCpuUseRatio()).append(",");
        sb.append(log.getSystemUseRatio()).append(",");
        sb.append(log.getUsedMemoryKb()).append(",");
        sb.append(log.getMemoryUseRatio()).append(",");
        sb.append(log.getTps()).append(",");
        sb.append(log.getIoWriteKb()).append(",");
        // Network fields
        if (log.getNics() != null && !log.getNics().isEmpty()) {
            sb.append(log.getNics().size()).append(",");

            for (NicInfo nic : log.getNics()) {
                sb.append(nic.getName()).append(";");
                sb.append(nic.getReceivePacketsRate()).append(";");
                sb.append(nic.getTransmitPacketsRate()).append(";");
                sb.append(nic.getReceiveKbRate()).append(";");
                sb.append(nic.getTransmitKbRate()).append(";");
            }
            // Remove the last '|' separator
            sb.setLength(sb.length() - 1);
        } else {
            sb.append("0");
        }
        sb.append(",");
        // Remaining fields
        sb.append(log.getTotalReceivePacketsRate()).append(",");
        sb.append(log.getTotalTransmitPacketsRate()).append(",");
        sb.append(log.getTotalReceiveKbRate()).append(",");
        sb.append(log.getTotalTransmitKbRate()).append(",");
        sb.append("\n");

        return sb.toString();
    }

    /**
     * @Description Convert the labeled log to a string for storage
     * @Date 2025/9/29
     **/
    private String labeledLog2Row(SystemNetworkWithLabel log) {
        StringBuilder sb = new StringBuilder();

        // Basic fields
        sb.append(log.getNodeId()).append(",");
        sb.append(log.getTimestamp()).append(",");
        // System fields
        sb.append(log.getCpuUseRatio()).append(",");
        sb.append(log.getSystemUseRatio()).append(",");
        sb.append(log.getUsedMemoryKb()).append(",");
        sb.append(log.getMemoryUseRatio()).append(",");
        sb.append(log.getTps()).append(",");
        sb.append(log.getIoWriteKb()).append(",");
        // Network fields
        if (log.getNics() != null && !log.getNics().isEmpty()) {
            sb.append(log.getNics().size()).append(",");

            for (NicInfo nic : log.getNics()) {
                sb.append(nic.getName()).append(";");
                sb.append(nic.getReceivePacketsRate()).append(";");
                sb.append(nic.getTransmitPacketsRate()).append(";");
                sb.append(nic.getReceiveKbRate()).append(";");
                sb.append(nic.getTransmitKbRate()).append(";");
            }
            // Remove the last '|' separator
            sb.setLength(sb.length() - 1);
        } else {
            sb.append("0");
        }
        sb.append(",");
        // Remaining fields
        sb.append(log.getTotalReceivePacketsRate()).append(",");
        sb.append(log.getTotalTransmitPacketsRate()).append(",");
        sb.append(log.getTotalReceiveKbRate()).append(",");
        sb.append(log.getTotalTransmitKbRate()).append(",");
        // Add the label field as the last field
        sb.append(log.getLabel());
        sb.append("\n");

        return sb.toString();
    }


    /**
     * @Description Parse labeled logs from raw log lines
     * @Date 2025/10/1
     **/
    public SystemNetworkWithLabel row2LabeledLog(String rawLog) {
        String log = rawLog.trim();
        SystemNetworkWithLabel result = new SystemNetworkWithLabel();
        String[] fields = log.split(",", -1);
        int index = 0;
        // Parse basic fields
        result.setNodeId(Integer.parseInt(fields[index++]));
        result.setTimestamp(Long.parseLong(fields[index++]));
        // Parse system fields
        result.setCpuUseRatio(Float.parseFloat(fields[index++]));
        result.setSystemUseRatio(Float.parseFloat(fields[index++]));
        result.setUsedMemoryKb(Long.parseLong(fields[index++]));
        result.setMemoryUseRatio(Float.parseFloat(fields[index++]));
        result.setTps(Integer.parseInt(fields[index++]));
        result.setIoWriteKb(Float.parseFloat(fields[index++]));
        // Parse network fields
        int nicCount = Integer.parseInt(fields[index++]);

        if (nicCount > 0 && index < fields.length) {
            List<NicInfo> nics = new ArrayList<>();
            String nicData = fields[index++];
            String[] nicArray = nicData.split("\\|");

            for (String nicStr : nicArray) {
                String[] nicFields = nicStr.split(";");
                NicInfo nic = new NicInfo();
                nic.setName(nicFields[0]);
                nic.setReceivePacketsRate(Integer.parseInt(nicFields[1]));
                nic.setTransmitPacketsRate(Integer.parseInt(nicFields[2]));
                nic.setReceiveKbRate(Float.parseFloat(nicFields[3]));
                nic.setTransmitKbRate(Float.parseFloat(nicFields[4]));
                nics.add(nic);
            }

            result.setNics(nics);
        }
        // Parse remaining system fields
        result.setTotalReceivePacketsRate(Integer.parseInt(fields[index++]));
        result.setTotalTransmitPacketsRate(Integer.parseInt(fields[index++]));
        result.setTotalReceiveKbRate(Float.parseFloat(fields[index++]));
        result.setTotalTransmitKbRate(Float.parseFloat(fields[index++]));
        // Parse label field
        if (index < fields.length) {
            result.setLabel(Integer.parseInt(fields[index]));
        }

        return result;
    }

    /**
     * @Description Merge multiple logs within the same second
     * @Date 2025/7/24
     **/
    private SystemNetworkLog mergeLogsAtSameTimestamp(List<SystemNetworkLog> logs, long timestamp, int nodeId, List<NicInfo> nicsTemplate) {
        SystemNetworkLog newLog = new SystemNetworkLog();
        // Set basic fields
        newLog.setNodeId(nodeId);
        newLog.setTimestamp(timestamp);
        newLog.setLogId(null);
        newLog.setEventId(logs.getFirst().getEventId());
        newLog.setNetworkId(logs.getFirst().getNetworkId());
        // Merge numerical fields (take average)
        newLog.setCpuUseRatio(avgFloat(logs, SystemNetworkLog::getCpuUseRatio));
        newLog.setSystemUseRatio(avgFloat(logs, SystemNetworkLog::getSystemUseRatio));
        newLog.setUsedMemoryKb(avgLong(logs, SystemNetworkLog::getUsedMemoryKb));
        newLog.setMemoryUseRatio(avgFloat(logs, SystemNetworkLog::getMemoryUseRatio));
        newLog.setTps(avgInt(logs, SystemNetworkLog::getTps));
        newLog.setIoWriteKb(avgFloat(logs, SystemNetworkLog::getIoWriteKb));
        newLog.setTotalReceivePacketsRate(avgInt(logs, SystemNetworkLog::getTotalReceivePacketsRate));
        newLog.setTotalTransmitPacketsRate(avgInt(logs, SystemNetworkLog::getTotalTransmitPacketsRate));
        newLog.setTotalReceiveKbRate(avgFloat(logs, SystemNetworkLog::getTotalReceiveKbRate));
        newLog.setTotalTransmitKbRate(avgFloat(logs, SystemNetworkLog::getTotalTransmitKbRate));
        // Merge nics fields
        if (!nicsTemplate.isEmpty()) {
            List<NicInfo> mergedNics = new ArrayList<>();

            for (NicInfo templateNic : nicsTemplate) {
                NicInfo mergedNic = new NicInfo();
                mergedNic.setName(templateNic.getName());
                final String nicName = templateNic.getName();
                mergedNic.setReceivePacketsRate(avgIntNic(logs, nicName, NicInfo::getReceivePacketsRate));
                mergedNic.setTransmitPacketsRate(avgIntNic(logs, nicName, NicInfo::getTransmitPacketsRate));
                mergedNic.setReceiveKbRate(avgFloatNic(logs, nicName, NicInfo::getReceiveKbRate));
                mergedNic.setTransmitKbRate(avgFloatNic(logs, nicName, NicInfo::getTransmitKbRate));
                mergedNics.add(mergedNic);
            }
            newLog.setNics(mergedNics);
        }

        return newLog;
    }

    /**
     * @Description Clone a log and set a new timestamp
     * @Date 2025/7/24
     **/
    private SystemNetworkLog cloneLogWithNewTimestamp(SystemNetworkLog source, long newTime) {
        SystemNetworkLog clone = new SystemNetworkLog();
        // Copy all fields from source to clone
        BeanUtils.copyProperties(source, clone);
        clone.setLogId(null);
        clone.setTimestamp(newTime);

        return clone;
    }

    /**
     * @Description Linearly interpolate between two logs
     * @Date 2025/7/24
     **/
    private SystemNetworkLog interpolateLogs(SystemNetworkLog before, SystemNetworkLog after, int befIdx, int aftIdx, int curIdx, long curTime) {
        float ratio = (float) (curIdx - befIdx) / (aftIdx - befIdx);
        SystemNetworkLog newLog = new SystemNetworkLog();
        newLog.setNodeId(before.getNodeId());
        newLog.setTimestamp(curTime);
        newLog.setEventId(before.getEventId());
        newLog.setNetworkId(before.getNetworkId());
        // Interpolation scalar field
        newLog.setCpuUseRatio(interpolateFloat(before.getCpuUseRatio(), after.getCpuUseRatio(), ratio));
        newLog.setSystemUseRatio(interpolateFloat(before.getSystemUseRatio(), after.getSystemUseRatio(), ratio));
        newLog.setUsedMemoryKb(interpolateLong(before.getUsedMemoryKb(), after.getUsedMemoryKb(), ratio));
        newLog.setMemoryUseRatio(interpolateFloat(before.getMemoryUseRatio(), after.getMemoryUseRatio(), ratio));
        newLog.setTps(interpolateInt(before.getTps(), after.getTps(), ratio));
        newLog.setIoWriteKb(interpolateFloat(before.getIoWriteKb(), after.getIoWriteKb(), ratio));
        newLog.setTotalReceivePacketsRate(interpolateInt(before.getTotalReceivePacketsRate(), after.getTotalReceivePacketsRate(), ratio));
        newLog.setTotalTransmitPacketsRate(interpolateInt(before.getTotalTransmitPacketsRate(), after.getTotalTransmitPacketsRate(), ratio));
        newLog.setTotalReceiveKbRate(interpolateFloat(before.getTotalReceiveKbRate(), after.getTotalReceiveKbRate(), ratio));
        newLog.setTotalTransmitKbRate(interpolateFloat(before.getTotalTransmitKbRate(), after.getTotalTransmitKbRate(), ratio));

        // Interpolation nics fields
        List<NicInfo> befNics = before.getNics();
        List<NicInfo> aftNics = after.getNics();

        if (befNics == null || befNics.isEmpty()) return newLog;

        List<NicInfo> newNics = interpolateNicInfos(befNics, aftNics, ratio);
        newLog.setNics(newNics);

        return newLog;
    }

    /**
     * @Description Get interpolated nics info between two logs
     * @Date 2025/11/22
     **/
    private List<NicInfo> interpolateNicInfos(List<NicInfo> befNics, List<NicInfo> aftNics, float ratio) {
        List<NicInfo> newNics = new ArrayList<>();

        for (int i = 0; i < befNics.size(); i++) {
            NicInfo befNic = befNics.get(i);
            NicInfo aftNic = aftNics.get(i);
            NicInfo nic = new NicInfo();
            nic.setName(befNic.getName());
            nic.setReceivePacketsRate(interpolateInt(befNic.getReceivePacketsRate(), aftNic.getReceivePacketsRate(), ratio));
            nic.setTransmitPacketsRate(interpolateInt(befNic.getTransmitPacketsRate(), aftNic.getTransmitPacketsRate(), ratio));
            nic.setReceiveKbRate(interpolateFloat(befNic.getReceiveKbRate(), aftNic.getReceiveKbRate(), ratio));
            nic.setTransmitKbRate(interpolateFloat(befNic.getTransmitKbRate(), aftNic.getTransmitKbRate(), ratio));
            newNics.add(nic);
        }

        return newNics;
    }

    /**
     * @Description Create zero value log
     * @Date 2025/7/24
     **/
    private SystemNetworkLog create0Log(int nodeId, long timestamp, List<NicInfo> nicsTemplate) {
        SystemNetworkLog log = new SystemNetworkLog();
        log.setNodeId(nodeId);
        log.setTimestamp(timestamp);
        // Set zero value for scalar fields
        log.setCpuUseRatio(.0f);
        log.setSystemUseRatio(.0f);
        log.setUsedMemoryKb(0L);
        log.setMemoryUseRatio(.0f);
        log.setTps(0);
        log.setIoWriteKb(.0f);
        log.setTotalReceivePacketsRate(0);
        log.setTotalTransmitPacketsRate(0);
        log.setTotalReceiveKbRate(.0f);
        log.setTotalTransmitKbRate(.0f);

        List<NicInfo> zeroNics = new ArrayList<>();

        for (NicInfo nicTemplate : nicsTemplate) {
            NicInfo nic = new NicInfo();
            nic.setName(nicTemplate.getName());
            nic.setReceivePacketsRate(0);
            nic.setTransmitPacketsRate(0);
            nic.setReceiveKbRate(.0f);
            nic.setTransmitKbRate(.0f);
            zeroNics.add(nic);
        }

        log.setNics(zeroNics);

        return log;
    }

    /**
     * @Description Find next hit index in boolean array
     * @Date 2025/7/24
     **/
    private int findNextHit(boolean[] exists, int start) {
        for (int i = start; i < exists.length; i++) {
            if (exists[i]) return i;
        }

        return -1;
    }

    /**
     * @Description Average calculation for float field
     * @Date 2025/7/24
     **/
    private Float avgFloat(List<SystemNetworkLog> logs, Function<SystemNetworkLog, Float> extractor) {
        float sum = .0f;
        int count = 0;

        for (SystemNetworkLog log : logs) {
            Float value = extractor.apply(log);
            if (value != null) {
                sum += value;
                count++;
            }
        }

        return count > 0 ? (sum / count) : .0f;
    }

    private Long avgLong(List<SystemNetworkLog> logs, Function<SystemNetworkLog, Long> extractor) {
        long sum = 0L;
        int count = 0;

        for (SystemNetworkLog log : logs) {
            Long value = extractor.apply(log);
            if (value != null) {
                sum += value;
                count++;
            }
        }

        return count > 0 ? (sum / count) : 0L;
    }

    private Integer avgInt(List<SystemNetworkLog> logs, Function<SystemNetworkLog, Integer> extractor) {
        int sum = 0;
        int count = 0;

        for (SystemNetworkLog log : logs) {
            Integer value = extractor.apply(log);
            if (value != null) {
                sum += value;
                count++;
            }
        }

        return count > 0 ? (sum / count) : 0;
    }

    /**
     * @Description Average calculation for int field in nics
     * @Date 2025/7/24
     **/
    private Integer avgIntNic(List<SystemNetworkLog> logs, String nicName, Function<NicInfo, Integer> extractor) {
        int sum = 0;
        int count = 0;

        for (SystemNetworkLog log : logs) {
            if (log.getNics() == null) continue;

            for (NicInfo nic : log.getNics()) {
                if (nicName.equals(nic.getName())) {
                    Integer value = extractor.apply(nic);
                    if (value != null) {
                        sum += value;
                        count++;
                    }
                }
            }
        }

        return count > 0 ? (sum / count) : 0;
    }

    private Float avgFloatNic(List<SystemNetworkLog> logs, String nicName, Function<NicInfo, Float> extractor) {
        float sum = 0f;
        int count = 0;

        for (SystemNetworkLog log : logs) {
            if (log.getNics() == null) continue;
            for (NicInfo nic : log.getNics()) {
                if (nicName.equals(nic.getName())) {
                    Float value = extractor.apply(nic);
                    if (value != null) {
                        sum += value;
                        count++;
                    }
                }
            }
        }

        return count > 0 ? (sum / count) : 0f;
    }

    /**
     * @Description Interpolation calculation
     * @Date 2025/7/24
     **/
    private Float interpolateFloat(Float before, Float after, float ratio) {
        before = before != null ? before : .0f;
        after = after != null ? after : .0f;

        return before + (after - before) * ratio;
    }

    private Long interpolateLong(Long before, Long after, float ratio) {
        before = before != null ? before : 0L;
        after = after != null ? after : 0L;

        return (long) (before + (after - before) * ratio);
    }

    private Integer interpolateInt(Integer before, Integer after, float ratio) {
        before = before != null ? before : 0;
        after = after != null ? after : 0;

        return (int) (before + (after - before) * ratio);
    }


    @Data
    private static class NicAccumulator {
        private int receivePacketsRate = 0;
        private int transmitPacketsRate = 0;
        private float receiveKbRate = 0f;
        private float transmitKbRate = 0f;
        private int receiveDroppedCount = 0;
        private int transmitDroppedCount = 0;
    }

    @Data
    private static class NormalLogs {
        long finalStartTime;
        long finalEndTime;
        private List<SystemNetworkLog> logs;
    }
}
