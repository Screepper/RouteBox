package com.routebox.manager.attack.link;

import com.routebox.common.constants.Constants;
import com.routebox.entity.attack.LinkControlArgs;
import com.routebox.entity.attack.LinkUpdateInfo;
import com.routebox.entity.index.AttackLog;
import com.routebox.entity.topology.DTO.LinkDTO;
import com.routebox.manager.InteractionManager;
import com.routebox.util.TimeUtil;
import com.routebox.manager.TopologyManager;

import java.util.*;

/**
 * @Description Link Broken Attack Handler
 * @Date 2025/11/23
**/
public class LinkDownAttackHandler extends LinkAttackHandler {

    public LinkDownAttackHandler(List<String> linkIds) {
        super(linkIds, "Link Down");
    }

    @Override
    public boolean attack() {
        StringBuilder apBuilder = new StringBuilder();
        apBuilder.append("Launched ")
                .append(ATTACK_TYPE)
                .append(" attack at ")
                .append(TimeUtil.getNowTimeStr())
                .append(", the following links are broken:\n");

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
                                .append("] is down.\n");
                        victimNodeIds.add(linkDTO.getSourceId());
                        victimNodeIds.add(linkDTO.getTargetId());
                    }
            );
        });

        InteractionManager.changeLinkStatus(new LinkUpdateInfo(Constants.STATUS_DOWN, LinkControlArgs.defaultArgs(), linkIds));

        postAttack(getAttackLog(apBuilder.toString(), new ArrayList<>(victimNodeIds)));

        return true;
    }

    private AttackLog getAttackLog(String attackProcess, List<Integer> victims) {
        AttackLog attackLog = new AttackLog();
        attackLog.setVictims(victims);
        attackLog.setAttackProcess(attackProcess);

        return attackLog;
    }

    @Override
    public void restoreState() {
        InteractionManager.changeLinkStatus(new LinkUpdateInfo(Constants.STATUS_UP, LinkControlArgs.defaultArgs(), linkIds));
    }

}
