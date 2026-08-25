package com.routebox.manager.attack.bgp;

import com.routebox.manager.attack.AttackHandler;
import com.routebox.manager.InteractionManager;
import com.routebox.manager.InteractionManager.WsOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @Description BGP Attack Handler
 * @Date 2025/4/12
**/
public abstract class BgpAttackHandler extends AttackHandler {

    protected final HashMap<Integer, String> focusedIdIpMap;
    protected final List<String> restoreCmdList;

    public BgpAttackHandler(Integer sourceNodeId, List<Integer> targetNodeIds, String attackType) {
        super(sourceNodeId, targetNodeIds);
        this.focusedIdIpMap = new HashMap<>();
        this.restoreCmdList = new ArrayList<>();
        ATTACK_TYPE = attackType;
    }

    @Override
    public void restoreState() {
        InteractionManager.sendCmdList(getAttackerId(), restoreCmdList);
    }
}
