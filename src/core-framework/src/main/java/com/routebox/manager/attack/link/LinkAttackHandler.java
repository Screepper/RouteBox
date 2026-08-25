package com.routebox.manager.attack.link;

import com.routebox.common.constants.Constants;
import com.routebox.entity.attack.LinkControlArgs;
import com.routebox.entity.attack.LinkUpdateInfo;
import com.routebox.manager.attack.AttackHandler;
import com.routebox.manager.InteractionManager;

import java.util.List;

/**
 * @Description Link Attack Handler
 * @Date 2025/6/26
**/
public abstract class LinkAttackHandler extends AttackHandler {
    protected final List<String> linkIds;

    public LinkAttackHandler(List<String> linkIds, String attackType) {
        this.linkIds = linkIds;
        ATTACK_TYPE = attackType;
    }

    @Override
    public void restoreState() {
        InteractionManager.changeLinkStatus(new LinkUpdateInfo(Constants.LINK_OP_TRAFFIC, LinkControlArgs.defaultArgs(), linkIds));
    }
}
