package com.routebox.manager.attack.link;

import com.routebox.common.constants.Constants;
import com.routebox.entity.attack.LinkControlArgs;
import com.routebox.entity.attack.LinkUpdateInfo;
import com.routebox.entity.index.AttackLog;
import com.routebox.entity.topology.DTO.LinkDTO;
import com.routebox.manager.InteractionManager;
import com.routebox.util.TimeUtil;
import com.routebox.manager.TopologyManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * @Description Link Congestion Attack Handler
 * @Date 2025/6/26
**/
public class LinkCongestionAttackHandler extends LinkAttackHandler {
    public LinkCongestionAttackHandler(List<String> linkIds) {
        super(linkIds, "Link Congestion");
    }

    @Override
    public boolean attack() {
        StringBuilder apBuilder = new StringBuilder();
        apBuilder.append("Launched ")
                .append(ATTACK_TYPE)
                .append(" attack at ")
                .append(TimeUtil.getNowTimeStr())
                .append(", the following links are congested:\n");

        HashSet<Integer> victimNodeIds = new HashSet<>();
        linkIds.forEach(linkId -> {
            Optional<LinkDTO> link = TopologyManager.topologyInfo.getLinks()
                    .stream()
                    .filter(linkDTO -> linkDTO.getId().equals(linkId))
                    .findFirst();
            link.ifPresent(linkDTO -> {
                apBuilder.append("Link")
                        .append(linkDTO.getId())
                        .append(": [")
                        .append(linkDTO.getSourceId())
                        .append("-")
                        .append(linkDTO.getTargetId())
                        .append("] is congested.\n");
                victimNodeIds.add(linkDTO.getSourceId());
                victimNodeIds.add(linkDTO.getTargetId());
            });
        });

        InteractionManager.changeLinkStatus(new LinkUpdateInfo(
                Constants.LINK_OP_TRAFFIC,
                new LinkControlArgs(random.nextInt(10) + 1, Constants.DEFAULT_LINK_DELAY, Constants.DEFAULT_LINK_LOSS),
                linkIds
        ));

        postAttack(getAttackLog(apBuilder.toString(), new ArrayList<>(victimNodeIds)));

        return true;
    }

    private AttackLog getAttackLog(String attackProcess, List<Integer> victims) {
        AttackLog attackLog = new AttackLog();
        attackLog.setVictims(victims);
        attackLog.setAttackProcess(attackProcess);

        return attackLog;
    }
}
