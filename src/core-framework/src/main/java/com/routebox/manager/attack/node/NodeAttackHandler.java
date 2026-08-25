package com.routebox.manager.attack.node;

import com.routebox.manager.attack.AttackHandler;

import java.util.List;

/**
 * @Description Node Attack Handler
 * @Date 2025/4/12
**/
public abstract class NodeAttackHandler extends AttackHandler {

    public NodeAttackHandler(List<Integer> targetNodeIds, String attackType) {
        super(null, targetNodeIds);
        ATTACK_TYPE = attackType;
    }
}
