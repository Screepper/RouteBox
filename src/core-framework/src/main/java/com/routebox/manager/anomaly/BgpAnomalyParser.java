package com.routebox.manager.anomaly;

import com.routebox.entity.index.BgpLog;
import com.routebox.entity.index.auxiliary.*;
import com.routebox.util.ListUtil;
import com.routebox.util.TimeUtil;
import com.routebox.manager.TopologyManager;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Description BGP Anomaly Constructor
 * @Date 2025/11/24
**/
public class BgpAnomalyParser extends AnomalyParser {
    private static final int MAX_ROW_DISPLAY_COUNT = 5;
    private static final int MAX_DISPLAY_COUNT = 15;

    public static String parse(Map<Integer, List<BgpLog>> bgpLogMap) {
        // Initialize anomaly category containers
        ChangeCategory addRoutes = new ChangeCategory("[ADD ROUTES]");
        ChangeCategory removeRoutes = new ChangeCategory("[REMOVE ROUTES]");
        ChangeCategory bestRouteChanges = new ChangeCategory("[BEST-ROUTE CHANGES]");
        ChangeCategory declareNetworks = new ChangeCategory("[DECLARE NETWORKS]");
        ChangeCategory undeclareNetworks = new ChangeCategory("[UNDECLARE NETWORKS]");
        ChangeCategory modifyNetworks = new ChangeCategory("[MODIFY NETWORKS]");
        ChangeCategory otherChanges = new ChangeCategory("[OTHER CHANGES]");

        if (bgpLogMap == null || bgpLogMap.isEmpty()) return "";

        bgpLogMap.forEach((nodeId, logs) -> {
            if (logs == null || logs.size() <= 1) return;

            for (int i = 0; i < logs.size() - 1; i++) {
                BgpLog prevLog = logs.get(i);
                BgpLog currLog = logs.get(i + 1);
                String aroundTime = TimeUtil.timestamp10ToTimeStr(prevLog.getTimestamp());
                // Collect various types of anomalies
                processOtherAnomalies(compareBasicProperties(prevLog, currLog, aroundTime), nodeId, otherChanges);
                // processOtherAnomalies(compareNeighbors(prevLog, currLog, aroundTime), nodeId, otherChanges);
                processNetworkDeclarations(compareNetworkDeclarations(prevLog, currLog, aroundTime), nodeId, declareNetworks, undeclareNetworks, modifyNetworks);
                processRouteEntries(compareRouteEntries(prevLog, currLog, aroundTime), nodeId, addRoutes, removeRoutes, bestRouteChanges);
                processOtherAnomalies(compareRouteMaps(prevLog, currLog, aroundTime), nodeId, otherChanges);
                processOtherAnomalies(compareNeighborRouteMaps(prevLog, currLog, aroundTime), nodeId, otherChanges);

            }
        });

        // Build final output
        return buildCategoryOutput(addRoutes) +
                buildCategoryOutput(removeRoutes) +
                buildCategoryOutput(bestRouteChanges) +
                buildCategoryOutput(declareNetworks) +
                buildCategoryOutput(undeclareNetworks) +
                buildCategoryOutput(modifyNetworks) +
                buildCategoryOutput(otherChanges);
    }

    /**
     * @Description Processing other types of anomalies
     * @Date 2025/11/12
     **/
    private static void processOtherAnomalies(List<AnomalyItem> anomalies, int nodeId, ChangeCategory otherChanges) {
        for (AnomalyItem anomaly : anomalies) {
            String info = anomaly.info;
            String timeStr = anomaly.timeStr;
            String fullRecord = getNodeType(nodeId) + nodeId + " " + info;

            otherChanges.addRecord(nodeId, fullRecord, timeStr);
        }
    }

    /**
     * @Description Processing network declaration related anomalies
     * @Date 2025/11/12
     **/
    private static void processNetworkDeclarations(List<AnomalyItem> anomalies, int nodeId, ChangeCategory
            declare, ChangeCategory undeclare, ChangeCategory modify) {
        for (AnomalyItem anomaly : anomalies) {
            String info = anomaly.info;
            String timeStr = anomaly.timeStr;
            String fullRecord = getNodeType(nodeId) + nodeId + " " + info;

            if (info.startsWith("declare ")) {
                declare.addRecord(nodeId, fullRecord, timeStr);
            } else if (info.startsWith("undeclare ")) {
                undeclare.addRecord(nodeId, fullRecord, timeStr);
            } else if (info.startsWith("modify ")) {
                modify.addRecord(nodeId, fullRecord, timeStr);
            }
        }
    }

    /**
     * @Description Processing route entry related anomalies
     * @Date 2025/11/12
     **/
    private static void processRouteEntries(List<AnomalyItem> anomalies, int nodeId, ChangeCategory add, ChangeCategory
            remove, ChangeCategory bestChange) {
        for (AnomalyItem anomaly : anomalies) {
            String info = anomaly.info;
            String timeStr = anomaly.timeStr;
            String fullRecord = getNodeType(nodeId) + nodeId + " " + info;

            if (info.startsWith("add ")) {
                add.addRecord(nodeId, fullRecord, timeStr);
            } else if (info.startsWith("remove ")) {
                remove.addRecord(nodeId, fullRecord, timeStr);
            } else if (info.contains("best-route change")) {
                bestChange.addRecord(nodeId, fullRecord, timeStr);
            }
        }
    }

    /**
     * @Description Build category output
     * @Date 2025/11/12
     **/
    private static String buildCategoryOutput(ChangeCategory category) {
        if (category.total == 0) return "";

        // Sort by nodeId
        List<Integer> nodeList = category.involvedNodes.stream().sorted(Comparator.comparingInt(Integer::valueOf)).toList();

        StringBuilder sb = new StringBuilder();
        // Build header
        sb.append(category.type).append(" Total: ").append(category.total)
                .append("; Timeline: ").append(category.earliestTimeStr).append(" - ").append(category.latestTimeStr)
                .append("; Involved: ");

        int involvedSize = nodeList.size();
        List<Integer> asNodeIds = TopologyManager.topologyInfo.getAsNodeIds();
        int fullAsSize = asNodeIds.size();

        // Build involved nodes
        if (involvedSize == fullAsSize) {
            sb.append("All AS\n");
        } else if ((involvedSize * 10) >= (fullAsSize * 8)) {
            List<Integer> excludeNodes = asNodeIds.stream().filter(asNodeId -> !nodeList.contains(asNodeId)).sorted().toList();
            sb.append("All AS except [");
            for (int i = 0; i < excludeNodes.size(); i++) {
                sb.append(excludeNodes.get(i));
                if (i != excludeNodes.size() - 1) {
                    sb.append(",");
                } else {
                    sb.append("]\n");
                }
            }
        } else {
            sb.append("[");
            for (int i = 0; i < nodeList.size(); i++) {
                if (i != nodeList.size() - 1) {
                    sb.append(nodeList.get(i)).append(",");
                } else {
                    sb.append(nodeList.get(i)).append("]\n");
                }
            }
        }

        // Ensure each nodeId has at least one record
        List<String> records = category.records;
        Map<Integer, List<String>> nodeRecordsMap = groupRecordsByNodeId(records);

        nodeRecordsMap.forEach((nodeId, recordsMap) -> recordsMap.forEach(record -> sb.append(record).append("\n")));

        // Collect one mandatory record for each nodeId
        List<String> mandatoryRecords = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> entry : nodeRecordsMap.entrySet()) {
            if (entry.getKey() != -1 && !entry.getValue().isEmpty()) {
                mandatoryRecords.add(entry.getValue().getFirst());
            }
        }

        // Collect optional records
        Set<String> mandatorySet = new HashSet<>(mandatoryRecords);
        List<String> optionalRecords = records.stream().filter(record -> !mandatorySet.contains(record)).toList();

        // Merge and determine final display records
        List<String> displayRecords;
        int mandatorySize = mandatoryRecords.size();

        if (mandatorySize >= MAX_DISPLAY_COUNT) {
            displayRecords = new ArrayList<>(mandatoryRecords);
        } else {
            displayRecords = new ArrayList<>(mandatoryRecords);
            int supplementCount = Math.min(MAX_DISPLAY_COUNT - mandatorySize, optionalRecords.size());
            displayRecords.addAll(optionalRecords.subList(0, supplementCount));
        }

        if (category.total > displayRecords.size()) {
            displayRecords.add("...");
        }

        displayRecords.forEach(record -> sb.append(record).append("\n"));

        return sb.toString();
    }

    private static List<AnomalyItem> compareBasicProperties(BgpLog prevLog, BgpLog currLog, String aroundTime) {
        List<AnomalyItem> anomalies = new ArrayList<>();

        if (!Objects.equals(prevLog.getAsNum(), currLog.getAsNum())) {
            anomalies.add(new AnomalyItem(
                    String.format("AsNum changed: %s -> %s at %s", prevLog.getAsNum(), currLog.getAsNum(), aroundTime),
                    aroundTime
            ));
        }
        if (!Objects.equals(prevLog.getPeersNum(), currLog.getPeersNum())) {
            anomalies.add(new AnomalyItem(
                    String.format("PeerNum changed: %s -> %s at %s", prevLog.getPeersNum(), currLog.getPeersNum(), aroundTime),
                    aroundTime
            ));
        }
        if (!Objects.equals(prevLog.getTotalNeighborsNum(), currLog.getTotalNeighborsNum())) {
            anomalies.add(new AnomalyItem(
                    String.format("NeighborNum changed: %s -> %s at %s", prevLog.getTotalNeighborsNum(), currLog.getTotalNeighborsNum(), aroundTime),
                    aroundTime
            ));
        }

        return anomalies;
    }

    private static List<AnomalyItem> compareNeighbors(BgpLog prevLog, BgpLog currLog, String aroundTime) {
        List<AnomalyItem> anomalies = new ArrayList<>();
        List<BgpNeighborInfo> prevNeighbors = ListUtil.getSafeImmutableList(prevLog.getNeighbors());
        List<BgpNeighborInfo> currNeighbors = ListUtil.getSafeImmutableList(currLog.getNeighbors());

        Map<String, BgpNeighborInfo> prevMap = prevNeighbors.stream().collect(Collectors.toMap(BgpAnomalyParser::buildNeighborKey, n -> n));
        Map<String, BgpNeighborInfo> currMap = currNeighbors.stream().collect(Collectors.toMap(BgpAnomalyParser::buildNeighborKey, n -> n));

        List<BgpNeighborInfo> added = currNeighbors.stream().filter(n -> !prevMap.containsKey(buildNeighborKey(n))).toList();
        List<BgpNeighborInfo> removed = prevNeighbors.stream().filter(n -> !currMap.containsKey(buildNeighborKey(n))).toList();
        List<String> changed = new ArrayList<>();

        for (Map.Entry<String, BgpNeighborInfo> entry : currMap.entrySet()) {
            String key = entry.getKey();
            BgpNeighborInfo curr = entry.getValue();
            if (prevMap.containsKey(key)) {
                BgpNeighborInfo prev = prevMap.get(key);
                if (!Objects.equals(prev, curr)) {
                    changed.add(String.format("%s", curr.getNeighborRouteId()));
                }
            }
        }

        if (!added.isEmpty()) {
            int total = added.size();
            List<String> displayList = added.stream().limit(MAX_ROW_DISPLAY_COUNT).map(BgpNeighborInfo::getNeighborRouteId).toList();
            String addedStr = buildDisplayStr(displayList, total, "added neighbor", "added neighbors");
            anomalies.add(new AnomalyItem(addedStr + " at " + aroundTime, aroundTime));
        }
        if (!removed.isEmpty()) {
            int total = removed.size();
            List<String> displayList = removed.stream().limit(MAX_ROW_DISPLAY_COUNT).map(BgpNeighborInfo::getNeighborRouteId).toList();
            String removedStr = buildDisplayStr(displayList, total, "removed neighbor", "removed neighbors");
            anomalies.add(new AnomalyItem(removedStr + " at " + aroundTime, aroundTime));
        }
        if (!changed.isEmpty()) {
            int total = changed.size();
            List<String> displayList = changed.stream().limit(MAX_ROW_DISPLAY_COUNT).toList();
            String changedStr = buildDisplayStr(displayList, total, "modified neighbor", "modified neighbors");
            anomalies.add(new AnomalyItem(changedStr + " at " + aroundTime, aroundTime));
        }

        return anomalies;
    }

    private static List<AnomalyItem> compareRouteEntries(BgpLog prevLog, BgpLog currLog, String aroundTime) {
        List<AnomalyItem> anomalies = new ArrayList<>();
        List<BgpRouteEntry> prevEntries = ListUtil.getSafeImmutableList(prevLog.getRouteEntries());
        List<BgpRouteEntry> currEntries = ListUtil.getSafeImmutableList(currLog.getRouteEntries());

        Map<String, List<BgpRouteEntry>> prevIpEntriesMap = prevEntries.stream().collect(Collectors.groupingBy(BgpRouteEntry::getIpPrefix));
        Map<String, List<BgpRouteEntry>> currIpEntriesMap = currEntries.stream().collect(Collectors.groupingBy(BgpRouteEntry::getIpPrefix));

        Set<String> addedIps = new HashSet<>(currIpEntriesMap.keySet());
        addedIps.removeAll(prevIpEntriesMap.keySet());
        Set<String> removedIps = new HashSet<>(prevIpEntriesMap.keySet());
        removedIps.removeAll(currIpEntriesMap.keySet());

        // Add routes
        List<String> addedRouteDesc = new ArrayList<>();
        int addedTotal = addedIps.size();

        for (String ip : addedIps.stream().limit(MAX_ROW_DISPLAY_COUNT).toList()) {
            List<BgpRouteEntry> entries = currIpEntriesMap.get(ip);
            Optional<BgpRouteEntry> bestExist = entries.stream().filter(BgpRouteEntry::isBest).findFirst();
            if (bestExist.isPresent()) {
                addedRouteDesc.add(ip);
            } else {
                addedRouteDesc.add(String.format("%s (%d secondary)", ip, entries.size()));
            }
        }

        if (!addedRouteDesc.isEmpty()) {
            String addedStr = buildDisplayStr(addedRouteDesc, addedTotal, "add 1", "add " + addedTotal);
            anomalies.add(new AnomalyItem(addedStr + " at " + aroundTime, aroundTime));
        }

        // Remove routes
        List<String> removedRouteDesc = new ArrayList<>();
        int removedTotal = removedIps.size();

        for (String ip : removedIps.stream().limit(MAX_ROW_DISPLAY_COUNT).toList()) {
            List<BgpRouteEntry> entries = prevIpEntriesMap.get(ip);
            Optional<BgpRouteEntry> bestOpt = entries.stream().filter(BgpRouteEntry::isBest).findFirst();
            if (bestOpt.isPresent()) {
                removedRouteDesc.add(ip);
            } else {
                removedRouteDesc.add(String.format("%s (%d secondary)", ip, entries.size()));
            }
        }

        if (!removedRouteDesc.isEmpty()) {
            String removedStr = buildDisplayStr(removedRouteDesc, removedTotal, "remove 1", "remove " + removedTotal);
            anomalies.add(new AnomalyItem(removedStr + " at " + aroundTime, aroundTime));
        }

        // Routes best path changed
        List<String> bestChangeDesc = new ArrayList<>();
        int bestChangeTotal = 0;
        Map<String, BgpRouteEntry> prevBestMap = prevEntries.stream()
                .filter(BgpRouteEntry::isBest)
                .collect(Collectors.toMap(BgpRouteEntry::getIpPrefix, e -> e, (prev, curr) -> curr));
        Map<String, BgpRouteEntry> currBestMap = currEntries.stream()
                .filter(BgpRouteEntry::isBest)
                .collect(Collectors.toMap(BgpRouteEntry::getIpPrefix, e -> e, (prev, curr) -> curr));
        Set<String> allIps = new HashSet<>(prevBestMap.keySet());
        allIps.addAll(currBestMap.keySet());

        for (String ip : allIps) {
            BgpRouteEntry prevBest = prevBestMap.get(ip);
            BgpRouteEntry currBest = currBestMap.get(ip);
            if (prevBest != null && currBest != null && !prevBest.getPath().equals(currBest.getPath())) {
                bestChangeDesc.add(String.format("%s (best path: %s -> %s)", ip, prevBest.getPath(), currBest.getPath()));
                bestChangeTotal++;
            }
        }

        if (!bestChangeDesc.isEmpty()) {
            String bestChangeStr = buildDisplayStr(bestChangeDesc.stream().limit(MAX_ROW_DISPLAY_COUNT).toList(), bestChangeTotal, "1 change", bestChangeTotal + " changes");
            anomalies.add(new AnomalyItem(bestChangeStr + " at " + aroundTime, aroundTime));
        }

        return anomalies;
    }

    private static List<AnomalyItem> compareNetworkDeclarations(BgpLog prevLog, BgpLog currLog, String aroundTime) {
        List<AnomalyItem> anomalies = new ArrayList<>();
        List<BgpNetworkDeclaration> prevDecls = ListUtil.getSafeImmutableList(prevLog.getNetworkIps());
        List<BgpNetworkDeclaration> currDecls = ListUtil.getSafeImmutableList(currLog.getNetworkIps());

        Map<String, BgpNetworkDeclaration> prevMap = prevDecls.stream().collect(Collectors.toMap(BgpAnomalyParser::buildNetworkDeclarationKey, d -> d));
        Map<String, BgpNetworkDeclaration> currMap = currDecls.stream().collect(Collectors.toMap(BgpAnomalyParser::buildNetworkDeclarationKey, d -> d));

        List<BgpNetworkDeclaration> added = currDecls.stream().filter(d -> !prevMap.containsKey(buildNetworkDeclarationKey(d))).toList();
        List<BgpNetworkDeclaration> removed = prevDecls.stream().filter(d -> !currMap.containsKey(buildNetworkDeclarationKey(d))).toList();
        List<String> changed = new ArrayList<>();

        for (Map.Entry<String, BgpNetworkDeclaration> entry : currMap.entrySet()) {
            String key = entry.getKey();
            BgpNetworkDeclaration curr = entry.getValue();
            if (prevMap.containsKey(key)) {
                BgpNetworkDeclaration prev = prevMap.get(key);
                if (!Objects.equals(prev, curr)) {
                    changed.add(String.format("%s (route-map: %s -> %s; mask: %s -> %s)", curr.getIpPrefix(), prev.getRouteMapName(), curr.getRouteMapName(), prev.getMask(), curr.getMask()));
                }
            }
        }

        if (!added.isEmpty()) {
            int total = added.size();
            List<String> displayList = added.stream()
                    .limit(MAX_ROW_DISPLAY_COUNT)
                    .map(d -> {
                        if (d.getRouteMapName() != null || d.getMask() != null)
                            return String.format("%s [route-map: %s, mask: %s]", d.getIpPrefix(), d.getRouteMapName(), d.getMask());
                        return d.getIpPrefix();
                    }).toList();
            String addedStr = buildDisplayStr(displayList, total, "declare 1", "declare " + total);
            anomalies.add(new AnomalyItem(addedStr + " at " + aroundTime, aroundTime));
        }
        if (!removed.isEmpty()) {
            int total = removed.size();
            List<String> displayList = removed.stream()
                    .limit(MAX_ROW_DISPLAY_COUNT)
                    .map(d -> {
                        if (d.getRouteMapName() != null || d.getMask() != null)
                            return String.format("%s [route-map: %s, mask: %s]", d.getIpPrefix(), d.getRouteMapName(), d.getMask());
                        return d.getIpPrefix();
                    }).toList();
            String removedStr = buildDisplayStr(displayList, total, "undeclare 1", "undeclare " + total);
            anomalies.add(new AnomalyItem(removedStr + " at " + aroundTime, aroundTime));
        }
        if (!changed.isEmpty()) {
            int total = changed.size();
            List<String> displayList = changed.stream().limit(MAX_ROW_DISPLAY_COUNT).toList();
            String changedStr = buildDisplayStr(displayList, total, "modify 1", "modify " + total);
            anomalies.add(new AnomalyItem(changedStr + " at " + aroundTime, aroundTime));
        }

        return anomalies;
    }

    private static List<AnomalyItem> compareRouteMaps(BgpLog prevLog, BgpLog currLog, String aroundTime) {
        List<AnomalyItem> anomalies = new ArrayList<>();
        List<BgpRouteMap> prevMaps = ListUtil.getSafeImmutableList(prevLog.getRouteMaps());
        List<BgpRouteMap> currMaps = ListUtil.getSafeImmutableList(currLog.getRouteMaps());

        Map<String, BgpRouteMap> prevMap = prevMaps.stream().collect(Collectors.toMap(BgpAnomalyParser::buildRouteMapKey, m -> m));
        Map<String, BgpRouteMap> currMap = currMaps.stream().collect(Collectors.toMap(BgpAnomalyParser::buildRouteMapKey, m -> m));

        List<BgpRouteMap> added = currMaps.stream().filter(m -> !prevMap.containsKey(buildRouteMapKey(m))).toList();
        List<BgpRouteMap> removed = prevMaps.stream().filter(m -> !currMap.containsKey(buildRouteMapKey(m))).toList();
        List<String> changed = new ArrayList<>();

        for (Map.Entry<String, BgpRouteMap> entry : currMap.entrySet()) {
            String key = entry.getKey();
            BgpRouteMap curr = entry.getValue();
            if (prevMap.containsKey(key)) {
                BgpRouteMap prev = prevMap.get(key);
                if (!Objects.equals(prev, curr)) {
                    changed.add(String.format("%s (seq-num: %s; action: %s -> %s; match: %s -> %s; set: %s -> %s)", curr.getName(), curr.getSequenceNumber(), prev.getAction(), curr.getAction(), prev.getMatchStatements(), curr.getMatchStatements(), prev.getSetStatements(), curr.getSetStatements()));
                }
            }
        }

        if (!added.isEmpty()) {
            int total = added.size();
            List<String> displayList = added.stream()
                    .limit(MAX_ROW_DISPLAY_COUNT)
                    .map(m -> String.format("%s (seq-num: %s; action: %s)", m.getName(), m.getSequenceNumber(), m.getAction()))
                    .toList();
            String addedStr = buildDisplayStr(displayList, total, "add 1 route-map", "add " + total + " route-maps");
            anomalies.add(new AnomalyItem(addedStr + " at " + aroundTime, aroundTime));
        }
        if (!removed.isEmpty()) {
            int total = removed.size();
            List<String> displayList = removed.stream()
                    .limit(MAX_ROW_DISPLAY_COUNT)
                    .map(m -> String.format("%s (seq-num: %s; action: %s)", m.getName(), m.getSequenceNumber(), m.getAction()))
                    .toList();
            String removedStr = buildDisplayStr(displayList, total, "remove 1 route-map", "remove " + total + " route-maps");
            anomalies.add(new AnomalyItem(removedStr + " at " + aroundTime, aroundTime));
        }
        if (!changed.isEmpty()) {
            int total = changed.size();
            List<String> displayList = changed.stream().limit(MAX_ROW_DISPLAY_COUNT).toList();
            String changedStr = buildDisplayStr(displayList, total, "modify 1 route-map", "modify " + total + " route-maps");
            anomalies.add(new AnomalyItem(changedStr + " at " + aroundTime, aroundTime));
        }

        return anomalies;
    }

    private static List<AnomalyItem> compareNeighborRouteMaps(BgpLog prevLog, BgpLog currLog, String aroundTime) {
        List<AnomalyItem> anomalies = new ArrayList<>();
        List<BgpNeighborRouteMap> prevNeighRM = ListUtil.getSafeImmutableList(prevLog.getNeighborRouteMaps());
        List<BgpNeighborRouteMap> currNeighRM = ListUtil.getSafeImmutableList(currLog.getNeighborRouteMaps());

        Map<String, BgpNeighborRouteMap> prevMap = prevNeighRM.stream().collect(Collectors.toMap(BgpAnomalyParser::buildNeighborRouteMapKey, m -> m));
        Map<String, BgpNeighborRouteMap> currMap = currNeighRM.stream().collect(Collectors.toMap(BgpAnomalyParser::buildNeighborRouteMapKey, m -> m));

        List<BgpNeighborRouteMap> added = currNeighRM.stream().filter(m -> !prevMap.containsKey(buildNeighborRouteMapKey(m))).toList();
        List<BgpNeighborRouteMap> removed = prevNeighRM.stream().filter(m -> !currMap.containsKey(buildNeighborRouteMapKey(m))).toList();

        if (!added.isEmpty()) {
            int total = added.size();
            List<String> displayList = added.stream()
                    .limit(MAX_ROW_DISPLAY_COUNT)
                    .map(m -> String.format("%s (direction: %s; route-map: %s)", m.getNeighborIp(), m.getDirection(), m.getRouteMapName()))
                    .toList();
            String addedStr = buildDisplayStr(displayList, total, "add 1 neighbor-route-map", "add " + total + " neighbor-route-maps");
            anomalies.add(new AnomalyItem(addedStr + " at " + aroundTime, aroundTime));
        }
        if (!removed.isEmpty()) {
            int total = removed.size();
            List<String> displayList = removed.stream()
                    .limit(MAX_ROW_DISPLAY_COUNT)
                    .map(m -> String.format("%s (direction: %s; route-map: %s)", m.getNeighborIp(), m.getDirection(), m.getRouteMapName()))
                    .toList();
            String removedStr = buildDisplayStr(displayList, total, "remove 1 neighbor-route-map", "remove " + total + " neighbor-route-maps");
            anomalies.add(new AnomalyItem(removedStr + " at " + aroundTime, aroundTime));
        }

        return anomalies;
    }

    private static Map<Integer, List<String>> groupRecordsByNodeId(List<String> records) {
        Map<Integer, List<String>> nodeRecordsMap = new HashMap<>();
        Pattern nodePattern = Pattern.compile("as(\\d+)");

        for (String record : records) {
            Matcher matcher = nodePattern.matcher(record);
            if (matcher.find()) {
                int nodeId = Integer.parseInt(matcher.group(1));
                nodeRecordsMap.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(record);
            } else {
                nodeRecordsMap.computeIfAbsent(-1, k -> new ArrayList<>()).add(record);
            }
        }

        return nodeRecordsMap;
    }

    private static String buildRouteMapKey(BgpRouteMap routeMap) {
        return routeMap.getName() + "|" + routeMap.getSequenceNumber();
    }

    private static String buildNeighborRouteMapKey(BgpNeighborRouteMap routeMap) {
        return routeMap.getNeighborIp() + "|" + routeMap.getDirection() + "|" + routeMap.getRouteMapName();
    }

    private static String buildNeighborKey(BgpNeighborInfo neighbor) {
        return neighbor.getNeighborRouteId();
    }

    private static String buildNetworkDeclarationKey(BgpNetworkDeclaration decl) {
        return decl.getIpPrefix();
    }

    private static String buildDisplayStr(List<String> displayList, int total, String singlePrefix, String multiPrefix) {
        if (total == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(total == 1 ? singlePrefix : multiPrefix);

        if (!displayList.isEmpty()) {
            sb.append(": ").append(String.join(", ", displayList));
            if (total > MAX_ROW_DISPLAY_COUNT) {
                sb.append(", and other ").append(total - MAX_ROW_DISPLAY_COUNT);
            }
        }

        return sb.toString();
    }

    private record AnomalyItem(String info, String timeStr) {
    }

    /**
     * @Description Anomaly Category Container
     * @Date 2025/11/12
     **/
    private static class ChangeCategory {
        final String type;
        int total;
        final Set<Integer> involvedNodes = new HashSet<>();
        final List<String> records = new ArrayList<>();
        String earliestTimeStr;
        String latestTimeStr;

        public ChangeCategory(String type) {
            this.type = type;
        }

        public void addRecord(int nodeId, String record, String timeStr) {
            records.add(record);
            involvedNodes.add(nodeId);
            total = records.size();

            if (earliestTimeStr == null || timeStr.compareTo(earliestTimeStr) < 0) {
                earliestTimeStr = timeStr;
            }
            if (latestTimeStr == null || timeStr.compareTo(latestTimeStr) > 0) {
                latestTimeStr = timeStr;
            }
        }
    }
}

