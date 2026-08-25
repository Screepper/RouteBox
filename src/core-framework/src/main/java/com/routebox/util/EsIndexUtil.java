package com.routebox.util;

import com.routebox.entity.index.IndexBase;
import com.routebox.manager.AttackManager;
import com.routebox.manager.TopologyManager;

/**
 * @Description Log Fill Tool
 * @Date 2025/3/27
**/
public class EsIndexUtil {
    /**
     * @Description Fill Base Log
     * @Date 2025/3/27
     **/
    public static void fillIndexBase(IndexBase indexBase) {
        indexBase.setLogId(StringUtil.generateId());
        indexBase.setEventId(AttackManager.getEventId());
        indexBase.setNetworkId(TopologyManager.topologyInfo.getNetworkId());
        indexBase.setNodeId(-1);
        indexBase.setTimestamp(TimeUtil.getNowTimestamp10());
    }

    /**
     * @Description Fill Base Log
     * @Date 2025/3/27
     **/
    public static void fillIndexBase(IndexBase indexBase, Integer nodeId, Long timestamp) {
        indexBase.setLogId(StringUtil.generateId());
        indexBase.setEventId(AttackManager.getEventId());
        indexBase.setNetworkId(TopologyManager.topologyInfo.getNetworkId());
        indexBase.setNodeId(nodeId);
        indexBase.setTimestamp(timestamp);
    }
}
