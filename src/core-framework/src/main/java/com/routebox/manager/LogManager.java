package com.routebox.manager;

import com.routebox.common.constants.ConfConstants;
import com.routebox.common.constants.Constants;
import com.routebox.entity.index.auxiliary.*;
import com.routebox.entity.index.*;
import com.routebox.entity.topology.TopologyInfo;
import com.routebox.service.EsService;
import com.routebox.util.*;

import static com.routebox.entity.index.BgpUpdateLog.MpReach;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javamrt.mrt.Advertisement;
import org.javamrt.mrt.BGPFileReader;
import org.javamrt.mrt.MRTRecord;
import org.javamrt.mrt.Withdraw;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;


/**
 * @Description Log Manager
 * @Date 2025/6/22
**/
@Component
@Slf4j
public class LogManager {
    private final EsService esService;

    public LogManager(EsService esService) {
        this.esService = esService;
    }

    /**
     * @Description Pull batch logs and save to ES every 60s
     * @Date 2025/7/16
     **/
    @Scheduled(cron = "${scheduler.logs-pull-cron}")
    public void pullLogs() {
        try {
            // Check topology info
            TopologyInfo topologyInfo = TopologyManager.topologyInfo;
            if (topologyInfo == null) throw new RuntimeException("Cannot initialize topology info");
            Integer networkId = topologyInfo.getNetworkId();

            // 1. Save topology log
            TopologyLog topologyLog = TopologyManager.getTopologyLog();
            esService.save(topologyLog);
            log.info("Save topology log success, node-healthy-ratio: {}, link-healthy-ratio: {}", topologyLog.getNodeHealthyRatio(), topologyLog.getLinkHealthyRatio());

            // 2. Save node-level logs
            // Parse local runningTopology.info file
            Map<String, VersionInfo> nodeNameVersionMap = new ConcurrentHashMap<>();
            Path baseTopologyFolder = Paths.get(ConfConstants.topologyPath());
            if (!Files.exists(baseTopologyFolder)) {
                Files.createDirectories(baseTopologyFolder);
            }

            Path path = Paths.get(ConfConstants.topologyPath() + "topology_" + networkId + ".info");
            if (!Files.exists(path)) {
                Files.createFile(path);
            } else {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    String[] items = line.split("\\|");
                    if (items.length != 6) continue;
                    nodeNameVersionMap.put(items[0], new VersionInfo(Integer.parseInt(items[3]), Long.parseLong(items[4]), Long.parseLong(items[5])));
                }
            }

            log.info("Pulling latest logs...");
            byte[] zipBytes = InteractionManager.downloadLogFiles(new ArrayList<>(topologyInfo.getAdjacencyList().keySet()), "ready");

            if (zipBytes == null || zipBytes.length == 0) {
                log.info("Latest logs are empty, error may occur in containers!");
                return;
            }

            String logName = networkId + "_" + StringUtil.generateId();
            String filename = logName + ".zip";
            FileUtil.saveBytesFile(zipBytes, filename);
            FileUtil.unzip(filename);
            Map<String, VersionLackInfo> lackMap = new ConcurrentHashMap<>();

            // Create virtual thread pool
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Path logFolder = Paths.get(ConfConstants.unzipPath() + logName);
                try (Stream<Path> stream = Files.list(logFolder)) {
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (Path nodeFolder : stream.toList()) {
                        if (!Files.isDirectory(nodeFolder)) continue;
                        // Process node log file asynchronously
                        futures.add(CompletableFuture.runAsync(() -> processNode(nodeFolder, nodeNameVersionMap, lackMap), executor));
                    }
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                } catch (IOException e) {
                    log.error("Process node log file error: {}", e.getMessage());
                }

                // If nodeNameVersionMap is still empty after processing, meaning no ready log is generated before pulling logs
                if (nodeNameVersionMap.isEmpty()) return;
                // Update runningTopology.info file
                StringBuilder versionContent = new StringBuilder();
                nodeNameVersionMap.forEach((nodeName, versionInfo) -> {
                            Integer nodeId = topologyInfo.getName2IdMap().getOrDefault(nodeName, -1);
                            versionContent
                                    .append(nodeName)
                                    .append("|")
                                    .append(nodeId)
                                    .append("|")
                                    .append(topologyInfo.getId2TypeMap().getOrDefault(nodeId, Constants.NODE_TYPE_AS))
                                    .append("|")
                                    .append(versionInfo.getRecordVersion())
                                    .append("|")
                                    .append(versionInfo.getStartTimestamp())
                                    .append("|")
                                    .append(versionInfo.getEndTimestamp())
                                    .append("\n");
                        }
                );
                Files.write(path, versionContent.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

                // Process nodes that need history logs
                List<Integer> lackNodeIds = lackMap.values().stream().map(VersionLackInfo::getNodeId).toList();
                if (!lackNodeIds.isEmpty()) {
                    log.info("Node {} need to pull history logs", lackNodeIds);
                    // Pull history log files
                    zipBytes = InteractionManager.downloadLogFiles(lackNodeIds, "history");

                    String historyLogName = networkId + "_" + StringUtil.generateId();
                    filename = historyLogName + ".zip";
                    log.info("Parsing history logs: {}", historyLogName);

                    FileUtil.saveBytesFile(zipBytes, filename);
                    FileUtil.unzip(filename);
                    Path historyFolder = Paths.get(ConfConstants.unzipPath() + historyLogName);
                    try (Stream<Path> historyStream = Files.list(historyFolder)) {
                        List<CompletableFuture<Void>> historyFutures = new ArrayList<>();
                        for (Path nodeFolder : historyStream.toList()) {
                            if (!Files.isDirectory(nodeFolder)) continue;
                            // Process history node log file asynchronously
                            historyFutures.add(CompletableFuture.runAsync(() -> processHistoryNode(nodeFolder, lackMap), executor));
                        }
                        CompletableFuture.allOf(historyFutures.toArray(new CompletableFuture[0])).join();
                    } catch (IOException e) {
                        log.error("Error when processing history node log file: {}", e.getMessage());
                    }
                }
            }

            // Clear local temporary files
            FileUtil.deleteDirectoryDepth(Paths.get(ConfConstants.unzipPath()));
            FileUtil.deleteDirectoryDepth(Paths.get(ConfConstants.zipPath()));
        } catch (Exception e) {
            log.info("Error when processing node log file: {}", e.getMessage());
        }
    }


    /**
     * @Description Process node log file
     * @Date 2025/7/3
     **/
    private void processNode(Path nodeFolder, Map<String, VersionInfo> nodeNameVersionMap, Map<String, VersionLackInfo> lackMap) {
        String nodeName = nodeFolder.getFileName().toString();
        // Get container nodeId
        Integer nodeId = TopologyManager.topologyInfo.getName2IdMap().get(nodeName);
        if (nodeId == null) return;

        Path readyFolder = nodeFolder.resolve("ready");
        if (!Files.exists(readyFolder)) return;

        try {
            // Parse version lock file
            Path versionLock = readyFolder.resolve(Constants.LOG_NAME_VERSION);
            if (!Files.exists(versionLock)) return;

            String versionContent = Files.readAllLines(versionLock).getFirst();
            String[] parts = versionContent.split("\\|");
            if (parts.length != 3) return;

            Integer newVersion = Integer.parseInt(parts[0]);
            Long startTimestamp = Long.parseLong(parts[1]);
            Long endTimestamp = Long.parseLong(parts[2]);

            // If record version is greater than or equal to new version, skip
            VersionInfo versionInfo = nodeNameVersionMap.getOrDefault(nodeName, new VersionInfo(0, 0L, 0L));
            Integer recordVersion = versionInfo.getRecordVersion();
            if (recordVersion >= newVersion) return;
            else {
                nodeNameVersionMap.put(nodeName, new VersionInfo(newVersion, startTimestamp, endTimestamp));
            }
            // If current version - record version is greater than 1, record lack version
            if (newVersion - recordVersion > 1) {
                lackMap.put(nodeName, new VersionLackInfo(nodeId, recordVersion, newVersion));
            }
            parseLogs(readyFolder, nodeId);
        } catch (IOException e) {
            log.error("Error when processing node log file {}: {}", nodeName, e.getMessage());
        }
    }

    /**
     * @Description Process history node log file
     * @Date 2025/7/3
     **/
    private void processHistoryNode(Path nodeFolder, Map<String, VersionLackInfo> anomalyMap) {
        String nodeName = nodeFolder.getFileName().toString();
        if (!anomalyMap.containsKey(nodeName)) return;

        VersionLackInfo lackInfos = anomalyMap.get(nodeName);
        if (lackInfos == null) return;

        int nodeId = lackInfos.getNodeId();
        int recordVersion = lackInfos.getRecordLatestVersion();
        int actualVersion = lackInfos.getActualVersion();

        Path historyFolder = nodeFolder.resolve("history");
        if (!Files.exists(historyFolder)) return;

        log.info("Parsing missing history logs of node {}: {}-{}", nodeName, recordVersion + 1, actualVersion - 1);
        for (int i = recordVersion + 1; i < actualVersion; i++) {
            Path historyLogFolder = historyFolder.resolve(i + "");
            if (!Files.exists(historyLogFolder) || !Files.exists(historyLogFolder.resolve(Constants.LOG_NAME_VERSION)))
                continue;
            try {
                parseLogs(historyLogFolder, nodeId);
            } catch (IOException e) {
                log.error("Error when parsing history log of node {}: {}", nodeName, e.getMessage());
            }
        }
    }

    /**
     * @Description Parse node logs
     * @Date 2025/7/3
     **/
    private void parseLogs(Path logFolder, int nodeId) throws IOException {
        List<IndexBase> logs = new ArrayList<>();

        // Parse System-Network Logs
        Path systemLog = logFolder.resolve(Constants.LOG_NAME_SYSTEM_NETWORK);
        if (Files.exists(systemLog)) {
            parseSystemNetworkLog(systemLog, nodeId, logs);
        }
        // Parse BGP Logs
        Path bgpLog = logFolder.resolve(Constants.LOG_NAME_BGP_CONFIG);
        if (Files.exists(bgpLog)) {
            parseBgpLog(bgpLog, nodeId, logs);
        }
        // Parse Router Logs
        Path routerLog = logFolder.resolve(Constants.LOG_NAME_ROUTER);
        if (Files.exists(routerLog)) {
            parseRouterLog(routerLog, nodeId, logs);
        }
        // Parse Ping Logs
        Path pingLog = logFolder.resolve(Constants.LOG_NAME_PING);
        if (Files.exists(pingLog)) {
            parsePingLog(pingLog, nodeId, logs);
        }
        // Parse BGP Update Logs
        Path bgpUpdateMRT = logFolder.resolve(Constants.LOG_NAME_BGP_UPDATE_BIN);
        if (Files.exists(bgpUpdateMRT)) {
            parseBgpUpdateLog(bgpUpdateMRT, nodeId, logs);
        }
        esService.bulkSave(logs);
    }

    /**
     * @Description Parse System-Network Logs
     * @Date 2025/5/29
     **/
    private void parseSystemNetworkLog(Path file, Integer nodeId, List<IndexBase> logs) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) return;

        for (String line : lines) {
            String[] items = line.split("\\|", 8);
            int index = 0;
            Long timestamp = Long.parseLong(items[index++]);
            Float cpuUseRatio = Float.parseFloat(items[index++]);
            Float systemUseRatio = Float.parseFloat(items[index++]);
            Long usedMemoryKb = Long.parseLong(items[index++]);
            Float memoryUseRatio = Float.parseFloat(items[index++]);
            Integer tps = Integer.parseInt(items[index++]);
            Float ioWriteKb = Float.parseFloat(items[index++]);

            String[] networkBlocks = items[index].split("\\|");
            ArrayList<NicInfo> nics = new ArrayList<>();

            for (String networkBlock : networkBlocks) {
                String[] nicSplit = networkBlock.split("-");
                int jndex = 0;
                String name = nicSplit[jndex++];
                Integer receivePacketsRate = Integer.parseInt(nicSplit[jndex++]);
                Integer transmitPacketsRate = Integer.parseInt(nicSplit[jndex++]);
                Float receiveKbRate = Float.parseFloat(nicSplit[jndex++]);
                Float transmitKbRate = Float.parseFloat(nicSplit[jndex]);

                nics.add(new NicInfo(
                        name,
                        receivePacketsRate,
                        transmitPacketsRate,
                        receiveKbRate,
                        transmitKbRate
                ));
            }

            Integer totalReceivePacketsRate = 0, totalTransmitPacketsRate = 0;
            Float totalReceiveKbRate = .0000f, totalTransmitKbRate = .0000f;

            if (!nics.isEmpty()) {
                for (NicInfo nic : nics) {
                    totalReceivePacketsRate += nic.getReceivePacketsRate();
                    totalTransmitPacketsRate += nic.getTransmitPacketsRate();
                    totalReceiveKbRate += nic.getReceiveKbRate();
                    totalTransmitKbRate += nic.getTransmitKbRate();
                }
            }

            SystemNetworkLog systemNetworkLog = new SystemNetworkLog(
                    cpuUseRatio, systemUseRatio,
                    usedMemoryKb, memoryUseRatio,
                    tps, ioWriteKb, nics,
                    totalReceivePacketsRate, totalTransmitPacketsRate,
                    totalReceiveKbRate, totalTransmitKbRate
            );
            EsIndexUtil.fillIndexBase(systemNetworkLog, nodeId, timestamp);
            logs.add(systemNetworkLog);
        }
    }

    /**
     * @Description Parse Ping Logs
     * @Date 2025/5/29
     **/
    private void parsePingLog(Path file, Integer nodeId, List<IndexBase> logs) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) return;

        for (String line : lines) {
            String[] items = line.split("\\|");
            if (items.length != 3) continue;

            int index = 0;
            Long timestamp = Long.parseLong(items[index++]);
            String pingIp = items[index++];
            Integer pingNodeId = TopologyManager.topologyInfo.getPingIp2IdMap().get(pingIp);
            if (pingNodeId == null) continue;
            Float delayMs = Float.parseFloat(items[index]);

            PingLog pingLog = new PingLog(pingNodeId, pingIp, delayMs);
            EsIndexUtil.fillIndexBase(pingLog, nodeId, timestamp);
            logs.add(pingLog);
        }
    }

    /**
     * @Description Parse Router Logs
     * @Date 2025/5/29
     **/
    private void parseRouterLog(Path file, Integer nodeId, List<IndexBase> logs) throws IOException {
        List<String> lines = Files.readAllLines(file);

        if (lines.isEmpty()) return;

        List<VtyshInfo> histories = new ArrayList<>();
        long startTimestamp = 0L;
        long endTimestamp = 0L;

        for (String line : lines) {
            String[] parts = line.trim().split(" ", 6);
            if (parts.length != 6 || !"BGP:".equals(parts[2])) continue;

            long timestamp = TimeUtil.frrDate2Timestamp10(parts[0] + "-" + parts[1]);
            if (startTimestamp == 0L) {
                startTimestamp = timestamp;
            }
            endTimestamp = timestamp;
            histories.add(new VtyshInfo(timestamp, parts[5]));
        }

        if (startTimestamp != 0L && endTimestamp != 0L) {
            RouterLog routerLog = new RouterLog(histories);
            EsIndexUtil.fillIndexBase(routerLog, nodeId, (startTimestamp + endTimestamp) / 2);
            logs.add(routerLog);
        }
    }

    /**
     * @Description Parse BGP Logs
     * @Date 2025/5/29
     **/
    private void parseBgpLog(Path file, Integer nodeId, List<IndexBase> logs) throws IOException {
        String content = Files.readString(file);
        String[] bgpLogBlocks = content.split("Masker");

        for (String bgpLogBlock : bgpLogBlocks) {
            String[] parts = bgpLogBlock.split("masker");

            if (parts.length != 4) continue;

            String timestampStr = parts[0].startsWith("\n") ? parts[0].substring(1) : parts[0];
            long timestamp;
            try {
                timestamp = Long.parseLong(timestampStr);
            } catch (NumberFormatException e) {
                return;
            }
            String[] lines = parts[1].split("\\n");
            BgpLog bgpLog = new BgpLog();
            List<BgpNeighborInfo> neighbors = new ArrayList<>();
            boolean inNeighborTable = false;

            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("BGP router")) {
                    String[] rowParts = line.split(" ");
                    bgpLog.setRouterId(rowParts[3].replace(",", ""));
                    bgpLog.setAsNum(Integer.parseInt(rowParts[7]));
                } else if (line.startsWith("RIB entries")) {
                    String[] rowParts = line.split(" ");
                    bgpLog.setRibEntriesNum(Integer.parseInt(rowParts[2].replace(",", "")));
                } else if (line.startsWith("Peers")) {
                    String[] rowParts = line.split(" ");
                    bgpLog.setPeersNum(Integer.parseInt(rowParts[1].replace(",", "")));
                } else if (line.startsWith("Neighbor")) {
                    inNeighborTable = true;
                } else if (line.startsWith("Total number")) {
                    String[] rowParts = line.split(" ");
                    bgpLog.setTotalNeighborsNum(Integer.parseInt(rowParts[4]));
                    inNeighborTable = false;
                } else if (inNeighborTable && !line.isEmpty()) {
                    String[] rowParts = line.trim().split("\\s+");

                    if (rowParts.length >= 11) {
                        neighbors.add(getBgpNeighborInfo(rowParts));
                    }
                }
            }

            bgpLog.setNeighbors(neighbors);

            List<String> lineList = List.of(parts[2].split("\\n"));
            List<BgpRouteEntry> bgpRouteEntries = IpBgpUtil.parseRouteEntries(lineList, null);
            bgpLog.setRouteEntries(bgpRouteEntries);

            String runningConfig = parts[3];
            lines = runningConfig.split("\\n");
            IpBgpUtil.parseRunningConfig(lines, bgpLog);
            bgpLog.setRunningConfig(runningConfig);

            EsIndexUtil.fillIndexBase(bgpLog, nodeId, timestamp);
            logs.add(bgpLog);
        }
    }

    /**
     * @Description Parse BGP Update Log
     * @Date 2026/4/29
     **/
    private void parseBgpUpdateLog(Path file, Integer nodeId, List<IndexBase> logs) throws IOException {
        BGPFileReader reader = new BGPFileReader(new File(file.toString()));
        while (!reader.eof()) {
            MRTRecord mrtRecord;
            try {
                mrtRecord = reader.readNext();
            } catch (Exception e) {
                mrtRecord = null;
            }

            if (mrtRecord == null) continue;
            try {
                switch (mrtRecord) {
                    case Advertisement advertisement -> {
                        String[] parts = advertisement.toString().split("\\|", -1);

                        BgpUpdateLog updateLog = new BgpUpdateLog(); // 6 + 22 + 2 boundary
                        updateLog.setUpdateType("advertise");
                        EsIndexUtil.fillIndexBase(updateLog, nodeId, Long.parseLong(parts[1]));
                        updateLog.setPeerIp(parts[3]);
                        updateLog.setPeerAS(parts[4]);
                        updateLog.setIpPrefix(parts[5]);
                        updateLog.setAsPath(parts[6].isEmpty() ? null : parts[6]); // 0
                        updateLog.setOrigin(parts[7].isEmpty() ? null : parts[7]); // 1
                        updateLog.setNextHop(parts[8].isEmpty() ? null : parts[8]); // 2
                        updateLog.setLocalPref(parts[9].isEmpty() ? null : Long.parseLong(parts[9])); // 3
                        updateLog.setMed(parts[10].isEmpty() ? null : Long.parseLong(parts[10])); // 4
                        updateLog.setCommunity(parts[11].isEmpty() ? null : parts[11].split(" ")); // 5
                        updateLog.setAtomicAggregator(parts[12].isEmpty() ? null : parts[12]); // 6
                        updateLog.setAggregator(parts[13].isEmpty() ? null : parts[13]); // 7
                        updateLog.setOriginatorId(parts[14].isEmpty() ? null : Long.parseLong(parts[14])); // 8
                        updateLog.setClusterList(parts[15].isEmpty() ? null : parts[15].split(" ")); // 9
                        updateLog.setDpa(parts[16].isEmpty() ? null : parts[16]); // 10
                        updateLog.setAdvertiser(parts[17].isEmpty() ? null : parts[17]); // 11
                        updateLog.setClusterId(parts[18].isEmpty() ? null : parts[18]); // 12
                        updateLog.setMpReach(parseMpReach(parts[19])); // 13
                        updateLog.setMpUnReach(parts[20].isEmpty() ? null : parts[20]); // 14
                        updateLog.setExtCommunities(parts[21].isEmpty() ? null : parts[21]); // 15
                        updateLog.setAsPathLimit(parts[22].isEmpty() ? null : parts[22]); // 21

                        logs.add(updateLog);
                    }
                    case Withdraw withdrawal -> {
                        String[] parts = withdrawal.toString().split("\\|", -1); // 6 + 1 boundary

                        BgpUpdateLog updateLog = new BgpUpdateLog();
                        updateLog.setUpdateType("withdraw");
                        EsIndexUtil.fillIndexBase(updateLog, nodeId, Long.parseLong(parts[1]));
                        updateLog.setPeerIp(parts[3]);
                        updateLog.setPeerAS(parts[4]);
                        updateLog.setIpPrefix(parts[5]);

                        logs.add(updateLog);
                    }
                    default -> {
                    }

                }
            } catch (Exception ignored) {
            }
        }
        reader.close();
    }

    /**
     * @Description Parse MpReach
     * @Date 2026/4/29
     **/
    private MpReach parseMpReach(String mpReach) {
        if (mpReach == null || mpReach.isEmpty()) return null;

        MpReach parsed = new MpReach();
        List<String> nextHopList = new ArrayList<>();
        List<String> nlriList = new ArrayList<>();

        String[] parts = mpReach.split(" ");
        for (String part : parts) {
            if (part.startsWith("HOP:")) {
                nextHopList.add(part.substring(4));
            } else if (part.startsWith("NRLI:")) {
                nlriList.add(part.substring(5));
            }
        }
        parsed.setNextHop(nextHopList.toArray(String[]::new));
        parsed.setNlri(nlriList.toArray(String[]::new));

        return parsed;
    }

    /**
     * @Description Parse BGP Neighbor Info
     * @Date 2025/6/7
     **/
    private BgpNeighborInfo getBgpNeighborInfo(String[] rowParts) {
        BgpNeighborInfo neighbor = new BgpNeighborInfo();
        neighbor.setNeighborRouteId(rowParts[0]);
        neighbor.setNeighborAsNum(Integer.parseInt(rowParts[2]));
        neighbor.setMessageReceivedNum(Integer.parseInt(rowParts[3]));
        neighbor.setMessageSentNum(Integer.parseInt(rowParts[4]));
        neighbor.setUpDownTime(rowParts[8]);
        neighbor.setStateOrReceivedNum(rowParts[9]);
        neighbor.setPrefixSentNum(Integer.parseInt(rowParts[10]));

        return neighbor;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class VersionInfo {
        private Integer recordVersion;
        private Long startTimestamp;
        private Long endTimestamp;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class VersionLackInfo {
        private Integer nodeId;
        private Integer recordLatestVersion;
        private Integer ActualVersion;
    }
}