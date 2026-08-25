package com.routebox.controller.http;

import com.routebox.common.constants.ConfConstants;
import com.routebox.common.constants.Constants;
import com.routebox.common.response.R;
import com.routebox.entity.attack.*;
import com.routebox.entity.attack.VO.AttackableInfo;
import com.routebox.entity.attack.VO.ForceRevokeInfo;
import com.routebox.entity.index.AttackLog;
import com.routebox.entity.page.PageQuery;
import com.routebox.entity.page.PageVO;
import com.routebox.manager.AttackManager;
import com.routebox.service.EsService;
import com.routebox.manager.TopologyManager;
import com.routebox.manager.attack.AttackHandler;
import com.routebox.manager.TopologyManager.TopologyOptions;
import com.routebox.util.TimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @Description Attack Controller
 * @Date 2025/4/25
 **/
@RestController
@RequestMapping("/attack")
@Slf4j
public class AttackController {
    private final EsService esService;

    public AttackController(EsService esService) {
        this.esService = esService;
    }

    private R<String> checkAttackPreconditions() {
        if (TopologyManager.isTopologyInactive()) return R.error("Initialize topology first");
        if (AttackManager.disableNewAttack()) return R.error("Disable previous attack first");
        if (AttackManager.checkAttackable() < 0) return R.error("Attack is not ready to trigger now");
        return null;
    }

    /**
     * @Description Load attack parameters
     * @Date 2025/12/14
     **/
    @PostMapping("/loadAttackableInfo")
    public R<AttackableInfo> loadAttackableInfo() {
        if (TopologyManager.isTopologyInactive()) return R.error("Initialize topology first");

        AttackableInfo attackableInfo = AttackManager.getAttackableInfo();

        return R.ok(attackableInfo);
    }

    /**
     * @Description Load attack records
     * @Date 2025/12/14
     **/
    @PostMapping("/loadAttackRecords")
    public R<PageVO<AttackLog>> loadAttackRecords(@RequestBody PageQuery pageQuery) {
        int limit = pageQuery.getPageSize();
        int start = limit * (pageQuery.getCurrentPage() - 1);
        PageVO<AttackLog> pageVO = esService.searchPage2PageVO(limit, start, AttackLog.class);

        return R.ok(pageVO);
    }

    @GetMapping("/checkAttackable")
    public R<Long> checkAttackable() {
        return R.ok(AttackManager.checkAttackable());
    }

    /**
     * @Description BGP-like Attacks
     * @Date 2025/4/25
     **/
    @PostMapping("/bgp/{attackType}")
    public R<String> bgpAttack(@PathVariable Integer attackType, @RequestBody BgpAttackInfo attackInfo) {
        R<String> preCheck = checkAttackPreconditions();
        if (preCheck != null) return preCheck;

        Integer attackerId = attackInfo.getAttackerId();
        List<Integer> targetIds = attackInfo.getTargetIds();

        // Check parameter validity
        if (attackType == null) return R.error("attackType cannot be null");
        if (attackType < 0) return R.error("attackType illegal: " + attackType);
        if (attackerId == null) return R.error("attackNodeId cannot be null");

        boolean checkNotAs = TopologyManager.checkNotNodes(List.of(attackerId), TopologyOptions.TYPE_NODE_AS);
        if (checkNotAs) return R.error("attackNodeId must be AS node");

        if (targetIds != null) {
            if (targetIds.contains(attackerId)) return R.error("Attack target cannot contain attacker");
            checkNotAs = TopologyManager.checkNotNodes(targetIds, TopologyOptions.TYPE_NODE_AS);
            if (checkNotAs) return R.error("Target nodes must be AS nodes");
//            if (attackType == Constants.ATTACK_PREFIX_INTERCEPT && targetIds.size() != 2)
//                return R.error("Prefix Intercept must set two target nodes, [customer, victim]");
        }

        AttackHandler attackHandler = AttackManager.getBgpAttackHandler(attackType, attackerId, targetIds);
        if (attackHandler == null) return R.error("Unsupported attackType: " + attackType);
        log.info("BGP AttackType: {}，Attacker: {}，Target Nodes: {}", attackType, attackerId, targetIds);

        String eventId = AttackManager.launchAttack(attackHandler);
        if (eventId != null) return R.ok("BGP attack success", eventId);

        return R.error("Attack launch failed, BGP-like attacks must meet certain conditions before they can be launched (prefix hijacking must meet: the target declares at least one ipPrefix, route leakage must meet: the attacker and victim are neighbors and the attacker has configured outbound configuration to the victim)");
    }

    /**
     * @Description Node-link Attacks
     * @Date 2025/6/26
     **/
    @PostMapping("/node/{attackType}")
    public R<String> nodeAttack(@PathVariable Integer attackType, @RequestBody NodeAttackInfo attackInfo) {
        R<String> preCheck = checkAttackPreconditions();
        if (preCheck != null) return preCheck;

        List<Integer> targetIds = attackInfo.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()) return R.error("Target nodes cannot be empty");

        boolean checkNotNodes = TopologyManager.checkNotNodes(targetIds, TopologyOptions.TYPE_NODE);
        if (checkNotNodes) return R.error("Target nodes contain non-existent nodes");

        AttackHandler handler = AttackManager.getNodeAttackHandler(attackType, targetIds);
        if (handler == null) return R.error("Unsupported attackType: " + attackType);
        log.info("Node AttackType: {}，Target nodes: {}", attackType, targetIds);

        String eventId = AttackManager.launchAttack(handler);
        if (eventId != null) return R.ok("Node attack success", eventId);

        return R.error("Attack launch failed");
    }

    /**
     * @Description Link-like Attacks
     * @Date 2025/6/26
     **/
    @PostMapping("/link/{attackType}")
    public R<String> linkAttack(@PathVariable Integer attackType, @RequestBody LinkAttackInfo attackInfo) {
        R<String> preCheck = checkAttackPreconditions();
        if (preCheck != null) return preCheck;

        List<String> targetIds = attackInfo.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()) return R.error("LinkIds cannot be empty");

        boolean checkNotLinks = TopologyManager.checkNotLinks(targetIds);
        if (checkNotLinks) return R.error("LinkIds contain non-existent links");

        AttackHandler handler = AttackManager.getLinkAttackHandler(attackType, targetIds);
        if (handler == null) return R.error("Unsupported attackType: " + attackType);
        log.info("Link AttackType: {}，Target links: {}", attackType, targetIds);

        String eventId = AttackManager.launchAttack(handler);
        if (eventId != null) return R.ok("Link attack success", eventId);

        return R.error("Attack launch failed");
    }

    /**
     * @Description Restore Topology State
     * @Date 2025/6/26
     **/
    @PostMapping("/revoke")
    public R<String> attackRevoke(@RequestBody Map<String, String> requestBody) {
        String attackId = requestBody.get("attackId");

        boolean result = AttackManager.revokeAttack(attackId);
        if (!result) return R.error("No attack event found with id: " + attackId);

        return R.ok("Attack event " + attackId + " revoke success", null);
    }

    /**
     * @Description Restore Topology State
     * @Date 2025/6/26
     **/
    @GetMapping("/forceRevoke")
    public R<ForceRevokeInfo> forceRevoke() {
        ForceRevokeInfo forceRevokeInfo = AttackManager.forceRevokeAttack();

        return R.ok(forceRevokeInfo);
    }
}
