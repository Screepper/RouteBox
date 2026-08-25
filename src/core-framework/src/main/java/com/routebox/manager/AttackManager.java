package com.routebox.manager;

import com.routebox.common.constants.ConfConstants;
import com.routebox.common.constants.Constants;
import com.routebox.entity.attack.AttackEvent;
import com.routebox.entity.attack.VO.AttackType;
import com.routebox.entity.attack.VO.AttackableInfo;
import com.routebox.entity.attack.VO.ForceRevokeInfo;
import com.routebox.entity.attack.VO.RunningAttackSummary;
import com.routebox.entity.index.AttackLog;
import com.routebox.entity.topology.DTO.NodeDTO;
import com.routebox.manager.attack.AttackHandler;
import com.routebox.manager.attack.bgp.*;
import com.routebox.manager.attack.link.LinkCongestionAttackHandler;
import com.routebox.manager.attack.link.LinkDownAttackHandler;
import com.routebox.manager.attack.link.LinkJitterAttackHandler;
import com.routebox.manager.attack.node.MemoryLeakAttackHandler;
import com.routebox.manager.attack.node.NodeDownAttackHandler;
import com.routebox.manager.attack.node.SystemOverloadAttackHandler;
import com.routebox.service.EsService;
import com.routebox.util.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description Attack Library Manager
 * @Date 2025/12/18
 **/
@Slf4j
public class AttackManager {
    public static AttackEvent currentAttackEvent = null;
    private static volatile AttackableInfo attackableInfo = null;

    public static AttackHandler getBgpAttackHandler(int type, int attackerId, List<Integer> targetIds) {
        return switch (type) {
            case Constants.ATTACK_PREFIX_HIJACKING -> new PrefixHijackingAttackHandler(attackerId, targetIds);
            case Constants.ATTACK_SUB_PREFIX_HIJACKING -> new SubPrefixHijackingAttackHandler(attackerId, targetIds);
            // case Constants.ATTACK_FAKE_PREFIX_HIJACKING -> new FakePrefixHijackingAttackHandler(attackerId, targetIds);
            case Constants.ATTACK_ROUTE_FLAPPING -> new RouteFlappingAttackHandler(attackerId);
//            case Constants.ATTACK_PREFIX_INTERCEPT -> new PrefixInterceptAttackHandler(attackerId, targetIds);
            case Constants.ATTACK_ROUTE_LEAKAGE -> new RouteLeakageAttackHandler(attackerId, targetIds);
            default -> null;
        };
    }

    public static AttackHandler getNodeAttackHandler(int type, List<Integer> targetIds) {
        return switch (type) {
            case Constants.ATTACK_NODE_DOWN -> new NodeDownAttackHandler(targetIds);
            case Constants.ATTACK_SYSTEM_OVERLOAD -> new SystemOverloadAttackHandler(targetIds);
            case Constants.ATTACK_MEMORY_LEAKAGE -> new MemoryLeakAttackHandler(targetIds);
            default -> null;
        };
    }

    public static AttackHandler getLinkAttackHandler(int type, List<String> targetIds) {
        return switch (type) {
            case Constants.ATTACK_LINK_DOWN -> new LinkDownAttackHandler(targetIds);
            case Constants.ATTACK_LINK_CONGESTION -> new LinkCongestionAttackHandler(targetIds);
            case Constants.ATTACK_LINK_JITTER -> new LinkJitterAttackHandler(targetIds);
//            case Constants.ATTACK_LINK_LOSS -> new LinkLossAttackHandler(targetIds);
            default -> null;
        };
    }

    /**
     * @Description Check whether the attack can trigger now
     **/
    public static Long checkAttackable() {
        return TimeUtil.getNowTimestamp10() - ConfConstants.detectWindow() - TopologyManager.earliestNormalTimestamp();
    }

    /**
     * @Description Lazily Load Attackable Info
     * @Date 2026/3/8
     **/
    public static AttackableInfo getAttackableInfo() {
        if (attackableInfo == null) {
            synchronized (AttackManager.class) {
                if (attackableInfo == null) {
                    attackableInfo = new AttackableInfo();
                    attackableInfo.setSupportAttackTypes(List.of(
                            // BGP-like
                            new AttackType(
                                    "BGP-like",
                                    "Prefix Hijacking",
                                    "(only for AS nodes) Announce other ASes’ IP prefixes (need attackerId and targetIds)\n",
                                    Constants.ATTACK_PREFIX_HIJACKING
                            ),
                            new AttackType(
                                    "BGP-like",
                                    "Sub-prefix Hijacking",
                                    "(only for AS nodes) Announce other ASes’ IP prefixes with longer mask  (need attackerId and targetIds)\n",
                                    Constants.ATTACK_SUB_PREFIX_HIJACKING
                            ),
                            // new AttackType(
                            //         "BGP-like",
                            //         "Fake-prefix Hijacking",
                            //         "(only for AS nodes) Announce other ASes’ IP prefixes with appended fake AS numbers (need attackerId and targetIds)",
                            //         Constants.ATTACK_FAKE_PREFIX_HIJACKING
                            // ),
//                            new AttackType(
//                                    "BGP-like",
//                                    "Prefix Interception",
//                                    "(only for AS nodes) Prefix Hijacking and modify own configuration to intercept traffic (need attackerId and targetIds)",
//                                    Constants.ATTACK_PREFIX_INTERCEPT
//                            ),
                            new AttackType(
                                    "BGP-like",
                                    "Route Flapping",
                                    "(only for AS nodes) Frequently making routing announcements to destabilize the routing system (need targetIds)",
                                    Constants.ATTACK_ROUTE_FLAPPING
                            ),
                            new AttackType(
                                    "BGP-like",
                                    "Route Leakage",
                                    "(only for AS nodes) Leak unauthorized local routing table entries to other ASes (need attackerId and targetIds)",
                                    Constants.ATTACK_ROUTE_LEAKAGE
                            ),
                            // Node-like
                            new AttackType(
                                    "Node-like",
                                    "Node Down",
                                    "(only for nodes) Simulate node failure to stop normal service (need targetIds)",
                                    Constants.ATTACK_NODE_DOWN
                            ),
                            new AttackType(
                                    "Node-like",
                                    "System Overload",
                                    "(only for nodes) Occupy node CPU resources exhaustively (need targetIds)",
                                    Constants.ATTACK_SYSTEM_OVERLOAD
                            ),
                            new AttackType(
                                    "Node-like",
                                    "Memory Leakage",
                                    "(only for nodes) Occupy node memory resources persistently (need targetIds)",
                                    Constants.ATTACK_MEMORY_LEAKAGE
                            ),
                            // Link-like
                            new AttackType(
                                    "Link-like",
                                    "Link Down",
                                    "(only for links) Break direct connectivity between two nodes (need targetLinkIds, not link number, its the linkId record in topologySummary.links)\n",
                                    Constants.ATTACK_LINK_DOWN
                            ),
                            new AttackType(
                                    "Link-like",
                                    "Link Congestion",
                                    "(only for links) Simulate link congestion to raise latency (need targetLinkIds)",
                                    Constants.ATTACK_LINK_CONGESTION
                            ),
                            new AttackType(
                                    "Link-like",
                                    "Link Jitter",
                                    "(only for links) Introduce random unstable delay on links (need targetLinkIds)",
                                    Constants.ATTACK_LINK_JITTER
                            )
//                            new AttackType(
//                                    "Link-like",
//                                    "Link Loss",
//                                    "(only for links) Randomly drop transmitted network packets (need targetLinkIds)",
//                                    Constants.ATTACK_LINK_LOSS
//                            )
                    ));
                }
            }
        }

        attackableInfo.setTopologySummary(TopologyManager.getTopologySummary());

        RunningAttackSummary event = null;
        if (currentAttackEvent != null) {
            event = new RunningAttackSummary(currentAttackEvent.getEventId(), currentAttackEvent.getStartTime());
        }

        attackableInfo.setRunningAttackSummary(event);

        return attackableInfo;

    }

    /**
     * @Description Initiate Attack, Return Attack Event ID
     * @Date 2025/5/10
     **/
    public static String launchAttack(AttackHandler handler) {
        String attackId = preAttackEvent(handler);
        boolean attackSuccess = handler.attack();

        if (!attackSuccess) {
            AttackManager.cancelCurrentAttack();
            return null;
        }

        return attackId;
    }

    /**
     * @Description Restore Topology State
     * @Date 2025/4/27
     **/
    public static boolean revokeAttack(String eventId) {
        log.info("Restore topology Status");
        if (currentAttackEvent == null || !currentAttackEvent.getEventId().equals(eventId)) return false;

        currentAttackEvent.getAttackHandler().restoreState();
        Long endTimestamp = TimeUtil.getNowTimestamp10();

        AttackLog attackLog = currentAttackEvent.getAttackLog();
        attackLog.setEndTimestamp(endTimestamp);

        EsService esService = SpringBeanUtil.getBean(EsService.class);
        if (esService != null) {
            esService.update(attackLog);
        }

        currentAttackEvent = null;

        return true;
    }

    /**
     * @Description Force Restore Topology State
     * @Date 2026/3/12
     **/
    public static ForceRevokeInfo forceRevokeAttack() {
        if (currentAttackEvent == null) return new ForceRevokeInfo();

        currentAttackEvent.getAttackHandler().restoreState();
        Long endTimestamp = TimeUtil.getNowTimestamp10();

        AttackLog attackLog = currentAttackEvent.getAttackLog();
        attackLog.setEndTimestamp(endTimestamp);

        EsService esService = SpringBeanUtil.getBean(EsService.class);
        if (esService != null) {
            esService.update(attackLog);
        }

        currentAttackEvent = null;

        ForceRevokeInfo forceRevokeInfo = new ForceRevokeInfo();
        forceRevokeInfo.setAttackType(attackLog.getAttackType());
        List<Integer> attackers = attackLog.getAttackers();
        if (attackers != null && !attackers.isEmpty()) {
            int attackerId = attackers.getFirst();
            for (NodeDTO nodeDTO : TopologyManager.topologyInfo.getNodes()) {
                if (nodeDTO.getId() == attackerId) {
                    forceRevokeInfo.setAttackerName(nodeDTO.getName());
                    break;
                }
            }
        }

        List<String> victimNames = new ArrayList<>();
        List<Integer> victims = attackLog.getVictims();
        for (NodeDTO nodeDTO : TopologyManager.topologyInfo.getNodes()) {
            if (victims.contains(nodeDTO.getId())) {
                victimNames.add(nodeDTO.getName());
            }
        }
        forceRevokeInfo.setVictimNames(victimNames);

        return forceRevokeInfo;
    }

    /**
     * @Description Init new attack event
     * @Date 2025/5/10
     **/
    public static String preAttackEvent(AttackHandler attackHandler) {
        String eventId = StringUtil.generateId(16);
        currentAttackEvent = new AttackEvent(eventId, TimeUtil.getNowTimestamp10(), null, null, attackHandler);

        return eventId;
    }

    public static String getEventId() {
        return currentAttackEvent == null ? null : currentAttackEvent.getEventId();
    }

    /**
     * @Description Cancel current attack event
     * @Date 2025/7/21
     **/
    public static void cancelCurrentAttack() {
        currentAttackEvent = null;
    }

    /**
     * @Description Update Attack Log
     * @Date 2025/7/18
     **/
    public static void saveAttackInfo(AttackLog attackLog) {
        if (attackLog != null && currentAttackEvent != null) {
            currentAttackEvent.setAttackLog(attackLog);
        }
    }

    /**
     * @Description Check if current attack event is running
     * @Date 2025/4/25
     **/
    public static boolean disableNewAttack() {
        return currentAttackEvent != null;
    }
}
