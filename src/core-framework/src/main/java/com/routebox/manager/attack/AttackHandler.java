package com.routebox.manager.attack;

import com.routebox.entity.index.*;
import com.routebox.manager.AttackManager;
import com.routebox.service.EsService;
import com.routebox.util.EsIndexUtil;
import com.routebox.util.SpringBeanUtil;
import com.routebox.manager.TopologyManager;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @Description Attack Handler
 * @Date 2025/3/29
**/
@Data
@NoArgsConstructor
@Component
public abstract class AttackHandler {
    protected static final Logger log = LoggerFactory.getLogger(AttackHandler.class);

    protected final Random random = new Random();
    private Integer attackerId;
    private List<Integer> targetIds;
    protected String ATTACK_TYPE = "Unknown";

    public AttackHandler(Integer attackerId, List<Integer> targetIds) {
        this.attackerId = attackerId;
        this.targetIds = targetIds;
    }

    /**
     * @Description Record Attack Log After Attack
     * @Date 2025/5/9
     **/
    protected void postAttack(AttackLog attackLog) {
        EsIndexUtil.fillIndexBase(attackLog);
        attackLog.setAttackType(ATTACK_TYPE);
        attackLog.setEndTimestamp((long) Integer.MAX_VALUE);

        AttackManager.saveAttackInfo(attackLog);

        EsService esService = SpringBeanUtil.getBean(EsService.class);
        if (esService != null) {
            esService.save(attackLog);
        }
    }

    /**
     * @Description Launch Attack
     * @Date 2025/3/29
     */
    public abstract boolean attack();

    /**
     * @Description Restore Topology State After Attack
     * @Date 2025/3/29
     **/
    public abstract void restoreState();
}