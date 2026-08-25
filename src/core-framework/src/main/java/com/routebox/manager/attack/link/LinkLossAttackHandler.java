//package com.routebox.manager.attack.link;
//
//import com.routebox.common.constants.Constants;
//import com.routebox.entity.attack.LinkControlArgs;
//import com.routebox.entity.attack.LinkUpdateInfo;
//import com.routebox.entity.index.AttackLog;
//import com.routebox.entity.topology.DTO.LinkDTO;
//import com.routebox.manager.InteractionManager;
//import com.routebox.util.TimeUtil;
//import com.routebox.manager.TopologyManager;
//
//import java.util.*;
//
/// **
// * @Description Link Packet Loss Attack Handler
// * @Date 2025/6/26
// * @Author Screep
// **/
//public class LinkLossAttackHandler extends LinkAttackHandler {
//    public LinkLossAttackHandler(List<String> linkIds) {
//        super(linkIds, "Link Loss");
//    }
//
//    @Override
//    public boolean attack() {
//        StringBuilder apBuilder = new StringBuilder();
//        apBuilder.append("Launched ")
//                .append(ATTACK_TYPE)
//                .append(" attack at ")
//                .append(TimeUtil.getNowTimeStr())
//                .append(", the following links are affected:\n");
//
//        HashSet<Integer> victimNodeIds = new HashSet<>();
//        linkIds.forEach(linkId -> {
//            Optional<LinkDTO> link = TopologyManager.topologyInfo.getLinks()
//                    .stream()
//                    .filter(linkDTO -> linkDTO.getId().equals(linkId))
//                    .findFirst();
//            link.ifPresent(linkDTO -> {
//                apBuilder.append("Link")
//                        .append(linkDTO.getId())
//                        .append(": [")
//                        .append(linkDTO.getSourceId())
//                        .append("-")
//                        .append(linkDTO.getTargetId())
//                        .append("] is affected.\n");
//                victimNodeIds.add(linkDTO.getSourceId());
//                victimNodeIds.add(linkDTO.getTargetId());
//            });
//        });
//
//        float[] loss = new float[]{0.1f, 0.2f};
//        InteractionManager.changeLinkStatus(new LinkUpdateInfo(
//                Constants.LINK_OP_TRAFFIC,
//                new LinkControlArgs(Constants.DEFAULT_LINK_BW, Constants.DEFAULT_LINK_DELAY, loss[random.nextInt(loss.length)]),
//                linkIds
//        ));
//
//        postAttack(getAttackLog(apBuilder.toString(), new ArrayList<>(victimNodeIds)));
//
//        return true;
//    }
//
//    private AttackLog getAttackLog(String attackProcess, List<Integer> victims) {
//        AttackLog attackLog = new AttackLog();
//        attackLog.setVictims(victims);
//        attackLog.setAttackProcess(attackProcess);
//
//        return attackLog;
//    }
//}
