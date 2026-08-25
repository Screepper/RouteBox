package com.routebox.manager.attack.node;

import java.util.List;

import com.routebox.common.constants.Constants;
import com.routebox.entity.index.AttackLog;
import com.routebox.entity.topology.BatchChangeStatus;
import com.routebox.manager.InteractionManager;
import com.routebox.util.TimeUtil;

/**
 * @Description Node Broken Attack Handler
 * @Date 2025/11/23
**/
public class NodeDownAttackHandler extends NodeAttackHandler {

    public NodeDownAttackHandler(List<Integer> targetNodeIds) {
        super(targetNodeIds, "Node Down");
    }

    @Override
    public boolean attack() {
        List<Integer> targetNodeIds = getTargetIds();
        InteractionManager.changeNodesStatus(new BatchChangeStatus(targetNodeIds, Constants.STATUS_DOWN));

        postAttack(getAttackLog("Launched " +
                ATTACK_TYPE +
                " attack at " +
                TimeUtil.getNowTimeStr() +
                ", nodes " + targetNodeIds + " are affected."
        ));

        return true;
    }

    private AttackLog getAttackLog(String attackProcess) {
        AttackLog attackLog = new AttackLog();
        attackLog.setVictims(getTargetIds());
        attackLog.setAttackProcess(attackProcess);

        return attackLog;
    }

    @Override
    public void restoreState() {
        InteractionManager.changeNodesStatus(new BatchChangeStatus(getTargetIds(), Constants.STATUS_UP));
    }
}
