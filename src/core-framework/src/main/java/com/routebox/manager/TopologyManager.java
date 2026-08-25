package com.routebox.manager;

import com.routebox.common.constants.ConfConstants;
import com.routebox.common.constants.Constants;
import com.routebox.entity.index.auxiliary.AdjacencyInfo;
import com.routebox.entity.index.TopologyLog;
import com.routebox.entity.index.auxiliary.NicInfo;
import com.routebox.entity.index.auxiliary.TopologyLinkInfo;
import com.routebox.entity.index.auxiliary.TopologyNodeInfo;
import com.routebox.entity.topology.*;
import com.routebox.entity.topology.DTO.LinkDTO;
import com.routebox.entity.topology.DTO.NodeDTO;
import com.routebox.entity.topology.DTO.TopologyInfoDTO;
import com.routebox.entity.topology.VO.LinkInfo;
import com.routebox.entity.topology.VO.NodeInfo;
import com.routebox.entity.topology.VO.TopologySummary;
import com.routebox.service.EsService;
import com.routebox.util.*;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description Topology Manage Tool
 * @Date 2025/3/27
 **/
@SuppressWarnings("unchecked")
@Slf4j
public class TopologyManager {
    public static TopologyInfo topologyInfo = null;
    private static Long EARLIEST_TIMESTAMP;

    public enum TopologyOptions {
        TYPE_NODE,
        TYPE_NODE_AS,
        TYPE_NODE_HOST,
    }

    /**
     * @Description Initialize TopologyInfo
     * @Date 2025/3/27
     **/
    public static boolean init() {
        TopologyInfoDTO topologyInfoDTO = InteractionManager.getTopologyInfo();
        if (topologyInfoDTO == null) return false;

        updateTopologyInfo(topologyInfoDTO, true);
        return true;
    }

    /**
     * @Description Create Topology Log
     * @Date 2025/7/16
     **/
    public static TopologyLog getTopologyLog() {
        // Update Topology Info
        updateTopologyInfo(InteractionManager.getTopologyInfo(), false);

        // Record Topology Log
        TopologyLog topologyLog = new TopologyLog();

        EsIndexUtil.fillIndexBase(topologyLog);
        topologyLog.setAdjacency(topologyInfo
                .getAdjacencyList()
                .entrySet()
                .stream()
                .map(entry -> new AdjacencyInfo(entry.getKey(), entry.getValue()))
                .toList());
        topologyLog.setNodes(topologyInfo
                .getNodes()
                .stream()
                .map(node -> new TopologyNodeInfo(node.getId(), node.getType(), node.getStatus(), node.getPhysical()))
                .toList());
        topologyLog.setLinks(topologyInfo
                .getLinks()
                .stream()
                .map(link -> new TopologyLinkInfo(link.getId(), link.getSourceId(), link.getTargetId(), link.getStatus()))
                .toList());

        float[] healthScore = calcHealthScore();
        topologyLog.setNodeHealthyRatio(healthScore[0]);
        topologyLog.setLinkHealthyRatio(healthScore[1]);

        return topologyLog;
    }

    /**
     * @Description Get Earliest Timestamp of Topology Log
     * @Date 2026/1/25
     **/
    public static Long earliestNormalTimestamp() {
        if (EARLIEST_TIMESTAMP == null) {
            synchronized (TopologyManager.class) {
                EsService esService = SpringBeanUtil.getBean(EsService.class);

                if (esService != null && EARLIEST_TIMESTAMP == null) {
                    List<TopologyLog> logs = esService.search(TopologyLog.class, true, 1);
                    if (logs != null && !logs.isEmpty()) {
                        EARLIEST_TIMESTAMP = logs.getFirst().getTimestamp();
                    }
                }
            }
        }

        return (EARLIEST_TIMESTAMP == null ? TimeUtil.getNowTimestamp10() : EARLIEST_TIMESTAMP) + ConfConstants.periodInterval();
    }

    /**
     * @Description Check if nodeId not in topology
     * @Date 2025/7/15
     **/
    public static boolean checkNotNodes(List<Integer> nodeIds, TopologyOptions option) {
        List<Integer> realNodeIds;

        if (option == TopologyOptions.TYPE_NODE) {
            realNodeIds = topologyInfo.getNodeIds();
        } else if (option == TopologyOptions.TYPE_NODE_AS) {
            realNodeIds = topologyInfo.getAsNodeIds();
        } else {
            realNodeIds = topologyInfo.getHostNodeIds();
        }

        for (Integer targetNodeId : nodeIds) {
            if (!realNodeIds.contains(targetNodeId)) return true;
        }

        return false;
    }

    /**
     * @Description Check if linkId not in topology
     * @Date 2025/7/15
     **/
    public static boolean checkNotLinks(List<String> linkIds) {
        List<String> realLinkIds = topologyInfo.getLinkIds();

        for (String targetLinkId : linkIds) {
            if (!realLinkIds.contains(targetLinkId)) return true;
        }

        return false;
    }

    /**
     * @Description Randomly declare IP prefixes and bind IP addresses
     * @Date 2025/7/16
     **/
    public static void randomDeclare() {
        InteractionManager.clearWsResponse();
        Random random = new Random();

        HashMap<Integer, List<String>> cmdListMap = new HashMap<>();
        topologyInfo.getAsNodeIds().forEach(nodeId -> {
            List<String> ipPrefixes = IpBgpUtil.generateRandomIpPrefixes(1 + random.nextInt(3));
            List<String> hostIps = IpBgpUtil.generateHostIps(ipPrefixes);
            List<String> cmdList = new ArrayList<>();
            StringBuilder cmd = new StringBuilder();

            hostIps.forEach(ip -> cmdList.add("ip addr add " + ip + " dev eth0"));
            cmd.append("vtysh -c 'configure' -c 'router bgp'");
            ipPrefixes.forEach(ip -> cmd.append(" -c ").append("'network ").append(ip).append("'"));
            cmdList.add(cmd.toString());

            cmdListMap.put(nodeId, cmdList);
        });

        InteractionManager.sendCmdList(cmdListMap);
    }

    /**
     * @Description Update nodes network status
     * @Date 2025/7/16
     **/
    public static void changeNodesNet(List<Integer> nodeIds, boolean status) {
        InteractionManager.changeNodesNet(new NodesNet(status, nodeIds));
        log.info("Nodes {} change network status to {}", nodeIds, status ? "Online" : "Offline");
    }

    /**
     * @Description Check if Topology is not running
     * @Date 2025/6/26
     **/
    public static boolean isTopologyInactive() {
        return topologyInfo == null || topologyInfo.getNetworkId() == null;
    }

    /**
     * @Description Get nics template from source nics
     * @Date 2025/7/24
     **/
    public static List<NicInfo> getNicsTemplate(int nodeId) {
        List<NicInfo> nicsTemplate = new ArrayList<>();

        topologyInfo.getNodeVethIpInfo().get(nodeId).forEach((neighborNodeId, nodeVethIpInfo) -> {
            NicInfo newNic = new NicInfo();
            newNic.setName(nodeVethIpInfo.getFromVeth());
            newNic.setReceivePacketsRate(0);
            newNic.setTransmitPacketsRate(0);
            newNic.setReceiveKbRate(0f);
            newNic.setTransmitKbRate(0f);
            nicsTemplate.add(newNic);
        });

        return nicsTemplate;
    }

    /**
     * @Description Transform TopologyInfoDTO to TopologyInfo
     * @Date 2025/3/27
     **/
    private static void updateTopologyInfo(TopologyInfoDTO topologyInfoDTO, boolean firstInit) {
        if (topologyInfoDTO == null) throw new RuntimeException("Topology Init Failed");

        if (firstInit) {
            // First time init TopologyInfo
            topologyInfo = new TopologyInfo();

            Integer networkId = topologyInfoDTO.getNetworkId();
            topologyInfo.setNetworkId(networkId);

            HashMap<Integer, List<Integer>> nodeList = new HashMap<>();
            HashMap<String, Integer> name2IdMap = new HashMap<>();
            HashMap<Integer, String> id2TypeMap = new HashMap<>();
            List<Integer> nodeIds = new ArrayList<>();
            List<String> linkIds = new ArrayList<>();
            List<Integer> asNodeIds = new ArrayList<>();
            List<Integer> hostNodeIds = new ArrayList<>();
            List<NodeDTO> nodes = topologyInfoDTO.getNodes();
            List<LinkDTO> links = topologyInfoDTO.getLinks();

            for (NodeDTO node : nodes) {
                Integer nodeId = node.getId();
                String name = node.getName();
                String type = node.getType();

                nodeList.put(nodeId, new ArrayList<>());
                name2IdMap.put(name, nodeId);
                id2TypeMap.put(nodeId, type);
                nodeIds.add(nodeId);

                if (type.equals(Constants.NODE_TYPE_AS)) {
                    asNodeIds.add(nodeId);
                } else if (type.equals(Constants.NODE_TYPE_HOST)) {
                    hostNodeIds.add(nodeId);
                }
            }

            for (LinkDTO link : links) {
                Integer sourceId = link.getSourceId();
                Integer targetId = link.getTargetId();
                nodeList.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(targetId);
                nodeList.computeIfAbsent(targetId, k -> new ArrayList<>()).add(sourceId);
                linkIds.add(link.getId());
            }

            nodeList.forEach((nodeId, neighbors) -> neighbors.sort(Integer::compareTo));

            String configContent = fetchRemoteNetworkConfig();

            if (configContent == null || configContent.isEmpty()) {
                log.error("Remote config content load failed, system initialize failed!");
                System.exit(1);
            }

            // Parse config.yaml to obtain the nodes veth pairs and ping-ip to node id map
            ParseResult parseResult = parseVethAndPingInfo(configContent, nodeIds, name2IdMap);
            HashMap<Integer, Map<Integer, NodeVethIpInfo>> nodeVethIpInfo = parseResult.nodeVethIpInfo;
            HashMap<String, Integer> pingIp2IdMap = parseResult.pingIp2IdMap;
            HashMap<Integer, String> id2PingIpMap = pingIp2IdMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getValue,
                            Map.Entry::getKey,
                            (oldValue, newValue) -> oldValue,
                            HashMap::new
                    ));

            topologyInfo.setNodes(nodes);
            topologyInfo.setLinks(links);
            topologyInfo.setAdjacencyList(nodeList);
            topologyInfo.setName2IdMap(name2IdMap);
            topologyInfo.setId2TypeMap(id2TypeMap);
            topologyInfo.setNodeIds(nodeIds);
            topologyInfo.setLinkIds(linkIds);
            topologyInfo.setAsNodeIds(asNodeIds);
            topologyInfo.setHostNodeIds(hostNodeIds);
            topologyInfo.setNodeVethIpInfo(nodeVethIpInfo);
            topologyInfo.setPingIp2IdMap(pingIp2IdMap);
            topologyInfo.setId2PingIpMap(id2PingIpMap);
        } else {
            // Update TopologyInfo
            topologyInfo.setNodes(topologyInfoDTO.getNodes());
            topologyInfo.setLinks(topologyInfoDTO.getLinks());
        }
    }

    /**
     * @Description Get Topology Summary
     * @Date 2025/7/16
     **/
    public static TopologySummary getTopologySummary() {
        TopologySummary topologySummary = new TopologySummary();
        if (topologyInfo == null) return null;

        List<NodeDTO> nodeDTOs = topologyInfo.getNodes();
        topologySummary.setSernesFrontendUrl("https://" + ConfConstants.sernesId() + ".frontend.sernes.cn/realsAdmin/#/reals/network");
        topologySummary.setRunningNetworkId(topologyInfo.getNetworkId());

        HashMap<Integer, String> id2PingIpMap = topologyInfo.getId2PingIpMap();

        List<NodeInfo> nodes = new ArrayList<>();
        for (NodeDTO nodeDTO : nodeDTOs) {
            NodeInfo nodeInfo = new NodeInfo();
            Integer nodeId = nodeDTO.getId();
            String nodeName = nodeDTO.getName();

            List<String> neighborVethInfo = new ArrayList<>();
            for (int neighborNodeId : topologyInfo.getAdjacencyList().get(nodeId)) {
                NodeVethIpInfo vethInfo = topologyInfo.getNodeVethIpInfo().get(nodeId).get(neighborNodeId);
                neighborVethInfo.add(String.format(
                        "[%s (%s: %s) -> %s (%s: %s)]",
                        nodeName,
                        vethInfo.getFromVeth(),
                        vethInfo.getFromIp(),
                        nodeDTOs.stream()
                                .filter(dto -> dto.getId() == neighborNodeId)
                                .map(NodeDTO::getName)
                                .findFirst()
                                .orElse("Unknown"),
                        vethInfo.getToVeth(),
                        vethInfo.getToIp()
                ));
            }

            nodeInfo.setNodeId(nodeId);
            nodeInfo.setName(nodeName);
            nodeInfo.setType(nodeDTO.getType().toUpperCase());
            nodeInfo.setStatus(nodeDTO.getStatus());
            nodeInfo.setNeighborVethInfo(neighborVethInfo);
            nodeInfo.setPingableIp(id2PingIpMap.get(nodeId));
            nodes.add(nodeInfo);
        }
        topologySummary.setNodes(nodes);

        List<LinkInfo> links = new ArrayList<>();
        for (LinkDTO linkDTO : topologyInfo.getLinks()) {
            LinkInfo linkInfo = new LinkInfo();
            linkInfo.setLinkId(linkDTO.getId());
            linkInfo.setSrcNodeId(linkDTO.getSourceId());
            linkInfo.setDstNodeId(linkDTO.getTargetId());
            linkInfo.setStatus(linkDTO.getStatus());
            links.add(linkInfo);
        }
        topologySummary.setLinks(links);

        topologySummary.setAsNodeCount(topologyInfo.getAsNodeIds().size());
        topologySummary.setHostNodeCount(topologyInfo.getHostNodeIds().size());
        topologySummary.setLinkCount(links.size());

        return topologySummary;
    }

    /**
     * @Description Download the remote running-network config files
     * @Date 2026/1/17
     **/
    private static String fetchRemoteNetworkConfig() {
        byte[] zipBytes = InteractionManager.downloadNetworkConfigFiles();

        if (zipBytes == null || zipBytes.length == 0) {
            log.info("Default config files of Network {} are empty!", topologyInfo.getNetworkId());
            return null;
        }

        String saveName = topologyInfo.getNetworkId() + "_" + StringUtil.generateId();
        String tmpZip = saveName + ".zip";
        FileUtil.saveBytesFile(zipBytes, tmpZip);
        FileUtil.unzip(tmpZip);

        // Parse file to yaml
        Path tmpFolder = Paths.get(ConfConstants.unzipPath() + saveName);
        if (!Files.exists(tmpFolder)) {
            log.info("Unzip folder {} not exists", tmpFolder);
            return null;
        }
        Path configFile = tmpFolder.resolve("config.yaml");
        if (!Files.exists(configFile)) {
            log.info("Config file {} not exists", configFile);
            return null;
        }
        String configContent = null;
        try {
            configContent = Files.readString(configFile);
        } catch (IOException e) {
            log.error("Read config file {} failed: {}", configFile, e.getMessage(), e);
        }

        return configContent;
    }

    /**
     * @Description Parse veth pairs and ping IP info from config.yaml
     * @Date 2026/1/17
     **/
    private static ParseResult parseVethAndPingInfo(String yamlContent, List<Integer> nodeIds, HashMap<String, Integer> name2IdMap) {
        HashMap<Integer, Map<Integer, NodeVethIpInfo>> nodeVethIpInfo = new HashMap<>();
        HashMap<String, Integer> pingIp2IdMap = new HashMap<>();

        nodeIds.forEach(nodeId -> nodeVethIpInfo.put(nodeId, new HashMap<>()));

        DumperOptions parsingOptions = new DumperOptions();
        parsingOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(parsingOptions);
        Map<String, Object> data = yaml.load(yamlContent);

        Map<String, Object> nodesBlock = (Map<String, Object>) data.get("nodes");
        HashMap<String, Map<String, Object>> nodeNicIpMap = new HashMap<>();
        if (nodesBlock != null) {
            for (Map.Entry<String, Object> entry : nodesBlock.entrySet()) {
                String nodeName = entry.getKey();
                Map<String, Object> nodeData = (Map<String, Object>) entry.getValue();
                Map<String, Object> vNICs = (Map<String, Object>) nodeData.get("vNICs");
                if (vNICs != null) {
                    nodeNicIpMap.put(nodeName, vNICs);
                }
            }
        }

        Map<String, Object> linkObjs = (Map<String, Object>) data.get("links");
        if (linkObjs == null) {
            linkObjs = (Map<String, Object>) data.get("logical_links");
        }
        if (linkObjs == null) throw new IllegalArgumentException("links or logical_links not found in config.yaml");
        List<Object> vethPairs = (List<Object>) linkObjs.get("veth_pairs");

        for (Object pairObj : vethPairs) {
            List<String> interfacePair = (List<String>) pairObj;
            if (interfacePair.size() != 2) {
                log.warn("Invalid veth pair format: {}", interfacePair);
                continue;
            }
            String iface1 = interfacePair.get(0);
            String iface2 = interfacePair.get(1);
            String[] parts1 = iface1.split(":", 2);
            String[] parts2 = iface2.split(":", 2);

            if (parts1.length != 2 || parts2.length != 2) {
                log.warn("Invalid interface format in pair: {} and {}", iface1, iface2);
                continue;
            }

            String nodeName1 = parts1[0];
            String ethName1 = parts1[1];
            String nodeName2 = parts2[0];
            String ethName2 = parts2[1];
            int nodeId1 = name2IdMap.get(nodeName1);
            int nodeId2 = name2IdMap.get(nodeName2);

            String ip1 = extractIpFromNic(nodeNicIpMap.get(nodeName1), ethName1);
            String ip2 = extractIpFromNic(nodeNicIpMap.get(nodeName2), ethName2);

            NodeVethIpInfo vethIpInfo1to2 = new NodeVethIpInfo(ethName1, ethName2, ip1, ip2);
            NodeVethIpInfo vethIpInfo2to1 = new NodeVethIpInfo(ethName2, ethName1, ip2, ip1);
            nodeVethIpInfo.get(nodeId1).put(nodeId2, vethIpInfo1to2);
            nodeVethIpInfo.get(nodeId2).put(nodeId1, vethIpInfo2to1);
        }

        for (Map.Entry<String, Object> entry : nodesBlock.entrySet()) {
            String nodeName = entry.getKey();
            Map<String, Object> nodeData = (Map<String, Object>) entry.getValue();
            String template = (String) nodeData.get("template");
            Map<String, Object> vNICs = (Map<String, Object>) nodeData.get("vNICs");

            if (vNICs == null) continue;
            String ip = null;
            if ("openeuler-hosts".equals(template)) {
                Map<String, Object> eth0 = (Map<String, Object>) vNICs.get("eth0");
                if (eth0 != null) {
                    List<String> ips = (List<String>) eth0.get("ip");
                    if (!ips.isEmpty()) {
                        ip = ips.getFirst().split("/")[0];
                    }
                }
            } else if ("frr".equals(template)) {
                String maxNIC = null;
                int maxEthNumber = -1;

                for (Map.Entry<String, Object> nicEntry : vNICs.entrySet()) {
                    String nicName = nicEntry.getKey();
                    if (nicName.startsWith("eth")) {
                        int ethNumber = Integer.parseInt(nicName.substring(3));
                        if (ethNumber > maxEthNumber) {
                            maxEthNumber = ethNumber;
                            maxNIC = nicName;
                        }
                    }
                }

                if (maxNIC != null) {
                    Map<String, Object> nicData = (Map<String, Object>) vNICs.get(maxNIC);
                    if (nicData != null) {
                        List<String> ips = (List<String>) nicData.get("ip");
                        if (!ips.isEmpty()) {
                            ip = ips.getFirst().split("/")[0];
                        }
                    }
                }
            }

            if (ip != null && name2IdMap.containsKey(nodeName)) {
                pingIp2IdMap.put(ip, name2IdMap.get(nodeName));
            }
        }

        return new ParseResult(nodeVethIpInfo, pingIp2IdMap);
    }

    private static String extractIpFromNic(Map<String, Object> vNICs, String ethName) {
        if (vNICs == null || !vNICs.containsKey(ethName)) {
            return null;
        }
        Map<String, Object> nicData = (Map<String, Object>) vNICs.get(ethName);
        List<String> ips = (List<String>) nicData.get("ip");
        if (ips != null && !ips.isEmpty()) {
            return ips.getFirst().split("/")[0];
        }
        return null;
    }

    /**
     * @Description Calculate the health score
     * @Date 2025/7/16
     **/
    private static float[] calcHealthScore() {
        int totalNodeCount = topologyInfo.getNodes().size();
        int totalLinkCount = topologyInfo.getLinks().size();
        int healthyNodeCount = 0;
        int healthyLinkCount = 0;

        for (NodeDTO node : topologyInfo.getNodes()) {
            if (Constants.STATUS_UP.equalsIgnoreCase(node.getStatus())) {
                healthyNodeCount++;
            }
        }

        for (LinkDTO link : topologyInfo.getLinks()) {
            if (Constants.STATUS_UP.equalsIgnoreCase(link.getStatus())) {
                healthyLinkCount++;
            }
        }

        DecimalFormat df = new DecimalFormat("#.00");
        String nodeScoreStr = df.format((float) healthyNodeCount / totalNodeCount);
        String linkScoreStr = df.format((float) healthyLinkCount / totalLinkCount);

        return new float[]{Float.parseFloat(nodeScoreStr), Float.parseFloat(linkScoreStr)};
    }

    private static class ParseResult {
        HashMap<Integer, Map<Integer, NodeVethIpInfo>> nodeVethIpInfo;
        HashMap<String, Integer> pingIp2IdMap;

        ParseResult(HashMap<Integer, Map<Integer, NodeVethIpInfo>> nodeVethIpInfo, HashMap<String, Integer> pingIp2IdMap) {
            this.nodeVethIpInfo = nodeVethIpInfo;
            this.pingIp2IdMap = pingIp2IdMap;
        }
    }
}