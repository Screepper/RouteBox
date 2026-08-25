package com.routebox.manager.attack.link;

import com.routebox.common.constants.Constants;
import com.routebox.entity.attack.LinkControlArgs;
import com.routebox.entity.attack.LinkUpdateInfo;
import com.routebox.entity.index.AttackLog;
import com.routebox.entity.topology.DTO.LinkDTO;
import com.routebox.manager.AttackManager;
import com.routebox.manager.InteractionManager;
import com.routebox.util.TimeUtil;
import com.routebox.manager.TopologyManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Description Link Delay Variation Attack Handler
 * @Date 2025/6/26
**/
public class LinkJitterAttackHandler extends LinkAttackHandler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public LinkJitterAttackHandler(List<String> linkIds) {
        super(linkIds, "Link Jitter");
    }

    @Override
    public boolean attack() {
        StringBuilder apBuilder = new StringBuilder();
        apBuilder.append("Launched ")
                .append(ATTACK_TYPE)
                .append(" attack at ")
                .append(TimeUtil.getNowTimeStr())
                .append(" the following links are affected:\n");

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
                        .append("] is affected.\n");
                victimNodeIds.add(linkDTO.getSourceId());
                victimNodeIds.add(linkDTO.getTargetId());
            });
        });

        long startTimestamp = System.currentTimeMillis();

        // Submit the task to the scheduler, run immediately and then run every 30s.
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Update link status
                InteractionManager.changeLinkStatus(new LinkUpdateInfo(
                        Constants.LINK_OP_TRAFFIC,
                        new LinkControlArgs(
                                Constants.DEFAULT_LINK_BW,
                                random.nextInt(2001) + 1000,  // 1000-3000ms random delay
                                Constants.DEFAULT_LINK_LOSS
                        ),
                        linkIds
                ));

                // Check exit condition
                long elapsedTime = System.currentTimeMillis() - startTimestamp;

                if (AttackManager.getEventId() == null || elapsedTime >= 1800000) {
                    // Stop the scheduler
                    scheduler.shutdown();
                    // Restore link status
                    restoreLinkStatus();
                }
            } catch (Exception e) {
                // Exception handling: stop the scheduler and restore link status
                scheduler.shutdown();
                restoreLinkStatus();
                Thread.currentThread().interrupt();
            }
        }, 0, 30, TimeUnit.SECONDS);

        postAttack(getAttackLog(apBuilder.toString(), new ArrayList<>(victimNodeIds)));

        return true;
    }

    /**
     * @Description Restore link status to default
     * @Date 2025/11/24
     **/
    private void restoreLinkStatus() {
        try {
            InteractionManager.changeLinkStatus(new LinkUpdateInfo(
                    Constants.LINK_OP_TRAFFIC,
                    LinkControlArgs.defaultArgs(),
                    linkIds
            ));
        } catch (Exception e) {
            log.error("Failed to restore link status", e);
        }
    }

    private AttackLog getAttackLog(String attackProcess, List<Integer> victims) {
        AttackLog attackLog = new AttackLog();
        attackLog.setVictims(victims);
        attackLog.setAttackProcess(attackProcess);

        return attackLog;
    }
}