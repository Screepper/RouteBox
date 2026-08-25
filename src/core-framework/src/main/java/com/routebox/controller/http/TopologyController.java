package com.routebox.controller.http;

import com.routebox.common.response.R;
import com.routebox.entity.attack.AttackRatioCalc;
import com.routebox.entity.attack.FetchAttackRecommendation;
import com.routebox.entity.topology.*;
import com.routebox.entity.topology.DTO.NodeNetDTO;
import com.routebox.entity.topology.VO.TopologySummary;
import com.routebox.manager.RecommendManager;
import com.routebox.manager.TopologyManager;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description Topology Controller
 * @Date 2025/3/26
**/
@RestController
@RequestMapping("/topology")
public class TopologyController {

    @PostMapping("/getTopology")
    public R<TopologySummary> getTopology() {
        if (TopologyManager.isTopologyInactive()) return R.error("Initialize topology first");

        TopologySummary topologySummary = TopologyManager.getTopologySummary();

        if (topologySummary == null) return R.error("Topology not found");
        return R.ok(topologySummary);
    }

    /**
     * @Description Change nodes' net status
     * @Date 2025/4/18
     **/
    @PostMapping("/changeNodesNet")
    public R<Void> changeNodesNet(@RequestBody NodeNetDTO nodeNetDTO) {
        if (TopologyManager.isTopologyInactive()) return R.error("Initialize topology first");

        List<NodeNet> nodeNets = nodeNetDTO.getNodesNet();
        List<Integer> onlineNodes = new ArrayList<>();
        List<Integer> offlineNodes = new ArrayList<>();

        for (NodeNet nodeNet : nodeNets) {
            if (nodeNet.getIsOnline()) {
                onlineNodes.add(nodeNet.getNodeId());
            } else {
                offlineNodes.add(nodeNet.getNodeId());
            }
        }

        if (!onlineNodes.isEmpty()) {
            TopologyManager.changeNodesNet(onlineNodes, true);
        }
        if (!offlineNodes.isEmpty()) {
            TopologyManager.changeNodesNet(offlineNodes, false);
        }

        return R.ok();
    }

    /**
     * @Description Randomly declare IP prefixes and bind them to corresponding IPs
     * @Tip Only Use in Test
     * @Date 2025/4/11
     **/
    @Deprecated
    @PostMapping("/randomDeclare")
    public R<Void> randomDeclare() {
        if (TopologyManager.isTopologyInactive()) return R.error("Initialize topology first");

        TopologyManager.randomDeclare();

        return R.ok();
    }

    /**
     * @Description Recommend attack targets
     * @Date 2025/3/28
     **/
    @PostMapping("/recommendTargets")
    public R<List<Recommended>> recommendTargets(@RequestBody FetchAttackRecommendation recommendation) {
        Integer attackCategory = recommendation.getAttackCategory();
        Integer attackerId = recommendation.getAttackerId();

        if (TopologyManager.isTopologyInactive()) return R.error("Initialize topology first");
        if (attackCategory == 0 && (attackerId == null || TopologyManager.checkNotNodes(List.of(attackerId), TopologyManager.TopologyOptions.TYPE_NODE_AS)))
            return R.error("Bgp Attack requires correct attackNodeId (AS), current attackerId: " + attackerId);
        if (TopologyManager.topologyInfo.getNodes().size() <= 2) return R.error("Topology requires at least 3 nodes");

        // Recommend attack targets
        return switch (attackCategory) {
            case 0 -> R.ok(RecommendManager.recommendBgpTargets(attackerId));
            case 1 -> R.ok(RecommendManager.recommendNodeTargets());
            case 2 -> R.ok(RecommendManager.recommendLinkTargets());
            default -> R.error("Attack category error, requires 0, 1, or 2");
        };
    }

    /**
     * @Description Calculate highest attack Score
     * @Date 2025/3/28
     **/
    @Deprecated
    @PostMapping("/calcAttackRatio")
    public R<List<Float>> calcAttackRatio(@RequestBody AttackRatioCalc attackRatioCalc) {
        List<Float> ratios = new ArrayList<>();
        int attackCategory = attackRatioCalc.getAttackCategory();
        List<Integer> attackerIds = attackRatioCalc.getAttackerIds();
        List<Object> targetIds = attackRatioCalc.getTargetIds();

        if (attackCategory == 0 && (attackerIds == null || attackerIds.isEmpty()))
            return R.error("Bgp Attack requires attackerIds");

        if (targetIds != null && !targetIds.isEmpty()) { // Calculate attack ratio for known target
            for (int i = 0; i < targetIds.size(); i++) {
                if (attackCategory == 0) {
                    ratios.add(RecommendManager.calcBgpAttackScore(attackerIds.get(i), (Integer) targetIds.get(i)));
                } else if (attackCategory == 1) {
                    ratios.add(RecommendManager.calcNodeAttackScore((Integer) targetIds.get(i)));
                } else if (attackCategory == 2) {
                    ratios.add(RecommendManager.calcLinkAttackScore((String) targetIds.get(i)));
                }
            }
        } else { // Calculate attack ratio for best target
            if (attackCategory == 0) {
                for (Integer attackerId : attackerIds) {
                    R<List<Recommended>> recommended = recommendTargets(new FetchAttackRecommendation(attackCategory, attackerId));
                    ratios.add(recommended.getData().getFirst().getExpectedImpactRatio());
                }
            } else if (attackCategory == 1) {
                R<List<Recommended>> recommended = recommendTargets(new FetchAttackRecommendation(attackCategory, null));
                ratios.add(recommended.getData().getFirst().getExpectedImpactRatio());
            } else if (attackCategory == 2) {
                R<List<Recommended>> recommended = recommendTargets(new FetchAttackRecommendation(attackCategory, null));
                ratios.add(recommended.getData().getFirst().getExpectedImpactRatio());
            }
        }


        return R.ok(ratios);
    }
}