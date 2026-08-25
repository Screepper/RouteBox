package com.routebox.entity.topology;

import com.routebox.entity.topology.DTO.LinkDTO;
import com.routebox.entity.topology.DTO.NodeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * @Description Topology Info
 * @Date 2025/11/23
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopologyInfo {
    private Integer networkId; // Network Id
    private List<NodeDTO> nodes; // Node List (Detailed Info)
    private List<LinkDTO> links; // Link List (Detailed Info)
    private HashMap<Integer, List<Integer>> adjacencyList; // Node Adjacency List
    private HashMap<String, Integer> name2IdMap; // Container Name to Id Mapping
    private HashMap<Integer, String> Id2TypeMap; // Id to Node Type Mapping
    private List<Integer> nodeIds; // NodeId List
    private List<String> linkIds; // LinkId List
    private List<Integer> asNodeIds; // AS NodeId List
    private List<Integer> hostNodeIds; // Host NodeId List
    private HashMap<Integer, Map<Integer, NodeVethIpInfo>> nodeVethIpInfo; // Node to Veth IP Info Mapping, {NodeId: {NeighborNodeId: {fromVethName, toVethName, fromIp, toIp}}}
    private HashMap<String, Integer> pingIp2IdMap; // Ping IP to NodeId Mapping
    private HashMap<Integer, String> id2PingIpMap; // NodeId to Ping IP Mapping
}