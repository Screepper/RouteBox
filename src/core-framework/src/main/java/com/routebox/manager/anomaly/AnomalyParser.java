package com.routebox.manager.anomaly;

import com.routebox.manager.TopologyManager;

/**
 * @Description Basic Constructor Class
 * @Date 2025/7/24
**/
public abstract class AnomalyParser {

    /**
     * @Description Get node type by nodeId
     * @Date 2025/11/24
     **/
    protected static String getNodeType(int nodeId){
        return TopologyManager.topologyInfo.getId2TypeMap().get(nodeId);
    }
}
