package com.routebox.manager.attack.bgp;

import com.routebox.entity.index.AttackLog;
import com.routebox.entity.index.auxiliary.BgpNeighborRouteMap;
import com.routebox.entity.topology.TopologyInfo;
import com.routebox.manager.InteractionManager;
import com.routebox.manager.TopologyManager;
import com.routebox.util.IpBgpUtil;
import com.routebox.util.TimeUtil;
import com.routebox.manager.InteractionManager.WsOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description Route Leakage Attack Handler
 * @Date 2025/3/29
**/
public class RouteLeakageAttackHandler extends BgpAttackHandler {

    public RouteLeakageAttackHandler(Integer attackerId, List<Integer> targetNodeIds) {
        super(attackerId, targetNodeIds, "Route Leakage");
    }

    @Override
    public boolean attack() {
        List<Integer> targetNodeIds = getTargetIds();
        TopologyInfo topologyInfo = TopologyManager.topologyInfo;

        // Only neighbors of attacker node can trigger this attack (from attacker leak to all target nodes)
        List<Integer> neighbors = topologyInfo.getAdjacencyList().get(getAttackerId());
        for (int targetNodeId : targetNodeIds) {
            if (!neighbors.contains(targetNodeId)) {
                log.warn("Node [{}] is not a neighbor of attacker node [{}], cannot trigger route leakage attack", targetNodeId, getAttackerId());
                return false;
            }
        }
        // Find the veth ip of neighbor ASes
        List<String> targetVethIps = topologyInfo.getNodeVethIpInfo().get(getAttackerId()).entrySet().stream()
                .filter(vethInfo -> targetNodeIds.contains(vethInfo.getKey()))
                .map(vethInfo -> vethInfo.getValue().getToIp())
                .toList();

        InteractionManager.clearWsResponse();
        InteractionManager.sendCmd(getAttackerId(), "vtysh -c 'show running-config'");
        ConcurrentHashMap<Integer, List<String>> wsResponseMap = InteractionManager.getWsResponse();

        // Query the out routes of attacker node
        List<String> targetOutRouteMapList = IpBgpUtil.parseNeighborRouteMap(wsResponseMap.get(getAttackerId())).stream()
                .filter(map -> map.getDirection().equals("out"))
                .filter(map -> targetVethIps.contains(map.getNeighborIp()))
                .map(map -> "neighbor " + map.getNeighborIp() + " route-map " + map.getRouteMapName() + " out")
                .toList();

        if (targetOutRouteMapList.isEmpty()) return false;

        InteractionManager.clearWsResponse();
        // Construct attack commands
        StringBuilder baseCmd = new StringBuilder("vtysh -c 'configure' -c 'router bgp' -c 'address-family ipv4 unicast'");
        targetOutRouteMapList.forEach(outMap -> baseCmd.append(" -c 'no ").append(outMap).append("'"));
        // Immediately apply the changes
        baseCmd.append(" -c 'exit-address-family'");
        String cmd = baseCmd.toString();
        restoreCmdList.add(cmd.replace("no ", ""));
        InteractionManager.sendCmd(getAttackerId(), cmd);

        postAttack(getAttackLog("Node [" +
                getAttackerId() +
                "] launched " +
                ATTACK_TYPE +
                " attack at " +
                TimeUtil.getNowTimeStr()
        ));

        return true;
    }

    private AttackLog getAttackLog(String attackProcess) {
        AttackLog attackLog = new AttackLog();
        attackLog.setAttackers(List.of(getAttackerId()));
        attackLog.setVictims(getTargetIds());
        attackLog.setAttackProcess(attackProcess);

        return attackLog;
    }
}