package com.routebox.manager.anomaly;

import com.routebox.entity.index.TopologyLog;
import com.routebox.entity.index.auxiliary.AdjacencyInfo;
import com.routebox.entity.index.auxiliary.TopologyLinkInfo;
import com.routebox.entity.index.auxiliary.TopologyNodeInfo;
import com.routebox.util.ListUtil;
import com.routebox.util.TimeUtil;
import java.util.*;

/**
 * @Description Topology Anomaly Constructor
 * @Date 2025/7/24
**/
public class TopologyAnomalyParser extends AnomalyParser {

    public static String parse(List<TopologyLog> topologyLogs) {
        StringBuilder sb = new StringBuilder();
        if (topologyLogs == null || topologyLogs.isEmpty()) return null;

        // Group by networkId
        Map<Integer, List<TopologyLog>> networkLogsMap = new HashMap<>();
        topologyLogs.forEach(log -> networkLogsMap.computeIfAbsent(log.getNetworkId(), k -> new ArrayList<>()).add(log));
        networkLogsMap.forEach((networkId, logs) -> {
            if (logs.size() <= 1) return;

            // Compare adjacent logs
            for (int i = 0; i < logs.size() - 1; i++) {
                StringBuilder networkSb = new StringBuilder();
                TopologyLog prevLog = logs.get(i);
                TopologyLog currLog = logs.get(i + 1);

                compareHealthyRatios(prevLog, currLog, networkSb);
                compareAdjacency(prevLog, currLog, networkSb);
                compareNodes(prevLog, currLog, networkSb);
                compareLinks(prevLog, currLog, networkSb);

                if (!networkSb.isEmpty()) {
                    String timeWindow = String.format("= %s =\n", TimeUtil.timestamp10ToTimeStr(currLog.getTimestamp()));
                    networkSb.insert(0, timeWindow);
                    sb.append(networkSb);
                }
            }
        });

        return sb.toString();
    }

    /**
     * @Description Compare node and link healthy ratios
     * @Date 2025/7/26
     **/
    private static void compareHealthyRatios(TopologyLog prevLog, TopologyLog currLog, StringBuilder sb) {
        if (!Objects.equals(prevLog.getNodeHealthyRatio(), currLog.getNodeHealthyRatio())) {
            sb.append("node-healthy ratio: ")
                    .append(prevLog.getNodeHealthyRatio() != null ? prevLog.getNodeHealthyRatio() : "null")
                    .append(" -> ").append(currLog.getNodeHealthyRatio() != null ? currLog.getNodeHealthyRatio() : "null").append("\n");
        }
        if (!Objects.equals(prevLog.getLinkHealthyRatio(), currLog.getLinkHealthyRatio())) {
            sb.append("link-healthy ratio: ")
                    .append(prevLog.getLinkHealthyRatio() != null ? prevLog.getLinkHealthyRatio() : "null")
                    .append(" -> ").append(currLog.getLinkHealthyRatio() != null ? currLog.getLinkHealthyRatio() : "null").append("\n");
        }
    }

    /**
     * @Description Compare node adjacency list changes
     * @Date 2025/7/26
     **/
    private static void compareAdjacency(TopologyLog prevLog, TopologyLog currLog, StringBuilder sb) {
        List<AdjacencyInfo> prevAdjacency = prevLog.getAdjacency() != null ? prevLog.getAdjacency() : new ArrayList<>();
        List<AdjacencyInfo> currAdjacency = currLog.getAdjacency() != null ? currLog.getAdjacency() : new ArrayList<>();

        Map<Integer, List<Integer>> prevAdjMap = new HashMap<>();
        prevAdjacency.forEach(info -> prevAdjMap.put(info.getNodeId(), info.getAdjacentNodeIds()));
        Map<Integer, List<Integer>> currAdjMap = new HashMap<>();
        currAdjacency.forEach(info -> currAdjMap.put(info.getNodeId(), info.getAdjacentNodeIds()));
        Set<Integer> allNodeIds = new HashSet<>(prevAdjMap.keySet());
        allNodeIds.addAll(currAdjMap.keySet());

        for (Integer nodeId : allNodeIds) {
            List<Integer> prevAdjNodes = prevAdjMap.getOrDefault(nodeId, new ArrayList<>());
            List<Integer> currAdjNodes = currAdjMap.getOrDefault(nodeId, new ArrayList<>());
            if (ListUtil.checkDiffList(prevAdjNodes, currAdjNodes)) {
                sb.append("Node").append(nodeId).append(" adjacency: ").append(prevAdjNodes).append(" -> ").append(currAdjNodes).append("\n");
            }
        }
    }

    /**
     * @Description Compare node information changes
     * @Date 2025/7/26
     **/
    private static void compareNodes(TopologyLog prevLog, TopologyLog currLog, StringBuilder sb) {
        List<TopologyNodeInfo> prevNodes = prevLog.getNodes() != null ? prevLog.getNodes() : new ArrayList<>();
        List<TopologyNodeInfo> currNodes = currLog.getNodes() != null ? currLog.getNodes() : new ArrayList<>();

        Map<Integer, TopologyNodeInfo> prevNodeMap = new HashMap<>();
        prevNodes.forEach(node -> prevNodeMap.put(node.getNodeId(), node));
        Map<Integer, TopologyNodeInfo> currNodeMap = new HashMap<>();
        currNodes.forEach(node -> currNodeMap.put(node.getNodeId(), node));
        Set<Integer> allNodeIds = new HashSet<>(prevNodeMap.keySet());
        allNodeIds.addAll(currNodeMap.keySet());

        for (Integer nodeId : allNodeIds) {
            TopologyNodeInfo prevNode = prevNodeMap.get(nodeId);
            TopologyNodeInfo currNode = currNodeMap.get(nodeId);

            if (prevNode == null) {
                sb.append("add node: ")
                        .append(nodeId).append(": type=").append(currNode.getType())
                        .append(", status=").append(currNode.getStatus())
                        .append(", physical node=").append(currNode.getPhysical()).append("\n");
            } else if (currNode == null) {
                sb.append("remove node: ")
                        .append(nodeId).append(": type=").append(prevNode.getType())
                        .append(", status=").append(prevNode.getStatus())
                        .append(", physical node=").append(prevNode.getPhysical()).append("\n");
            } else {
                if (!Objects.equals(prevNode.getType(), currNode.getType()) ||
                        !Objects.equals(prevNode.getStatus(), currNode.getStatus()) ||
                        !Objects.equals(prevNode.getPhysical(), currNode.getPhysical())) {
                    if (!Objects.equals(prevNode.getType(), currNode.getType())) {
                        sb.append("node").append(nodeId).append(" type: ").append(prevNode.getType()).append(" -> ").append(currNode.getType()).append("\n");
                    }
                    if (!Objects.equals(prevNode.getStatus(), currNode.getStatus())) {
                        sb.append("node").append(nodeId).append(" status: ").append(prevNode.getStatus()).append(" -> ").append(currNode.getStatus()).append("\n");
                    }
                    if (!Objects.equals(prevNode.getPhysical(), currNode.getPhysical())) {
                        sb.append("node").append(nodeId).append(" physical type: ").append(prevNode.getPhysical()).append(" -> ").append(currNode.getPhysical()).append("\n");
                    }
                }
            }
        }
    }

    /**
     * @Description Compare link changes
     * @Date 2025/7/26
     **/
    private static void compareLinks(TopologyLog prevLog, TopologyLog currLog, StringBuilder sb) {
        List<TopologyLinkInfo> prevLinks = prevLog.getLinks() != null ? prevLog.getLinks() : new ArrayList<>();
        List<TopologyLinkInfo> currLinks = currLog.getLinks() != null ? currLog.getLinks() : new ArrayList<>();

        Map<String, TopologyLinkInfo> prevLinkMap = new HashMap<>();
        prevLinks.forEach(link -> prevLinkMap.put(link.getLinkId(), link));
        Map<String, TopologyLinkInfo> currLinkMap = new HashMap<>();
        currLinks.forEach(link -> currLinkMap.put(link.getLinkId(), link));
        Set<String> allLinkIds = new HashSet<>(prevLinkMap.keySet());
        allLinkIds.addAll(currLinkMap.keySet());

        for (String linkId : allLinkIds) {
            TopologyLinkInfo prevLink = prevLinkMap.get(linkId);
            TopologyLinkInfo currLink = currLinkMap.get(linkId);
            if (prevLink == null) {
                sb.append("add link: ")
                        .append(linkId).append(": source node=").append(currLink.getSourceNodeId())
                        .append(", target node=").append(currLink.getTargetNodeId())
                        .append(", status=").append(currLink.getStatus()).append("\n");
            } else if (currLink == null) {
                sb.append("remove link: ")
                        .append(linkId).append(": source node=").append(prevLink.getSourceNodeId())
                        .append(", target node=").append(prevLink.getTargetNodeId())
                        .append(", status=").append(prevLink.getStatus()).append("\n");
            } else {
                if (!Objects.equals(prevLink.getSourceNodeId(), currLink.getSourceNodeId()) ||
                        !Objects.equals(prevLink.getTargetNodeId(), currLink.getTargetNodeId()) ||
                        !Objects.equals(prevLink.getStatus(), currLink.getStatus())) {
                    if (!Objects.equals(prevLink.getSourceNodeId(), currLink.getSourceNodeId())) {
                        sb.append("link").append(linkId).append(" source node: ").append(prevLink.getSourceNodeId()).append(" -> ").append(currLink.getSourceNodeId()).append("\n");
                    }
                    if (!Objects.equals(prevLink.getTargetNodeId(), currLink.getTargetNodeId())) {
                        sb.append("link").append(linkId).append(" target node: ").append(prevLink.getTargetNodeId()).append(" -> ").append(currLink.getTargetNodeId()).append("\n");
                    }
                    if (!Objects.equals(prevLink.getStatus(), currLink.getStatus())) {
                        sb.append("link").append(linkId).append(" status: ")
                                .append(prevLink.getStatus()).append(" -> ").append(currLink.getStatus()).append("\n");
                    }
                }
            }
        }
    }

}
