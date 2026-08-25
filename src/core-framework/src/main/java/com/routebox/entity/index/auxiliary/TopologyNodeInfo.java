package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Topology Node Info
 * @Date 2025/7/20
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopologyNodeInfo {
    private Integer nodeId; // NodeId
    private String type; // Node Type
    private String status; // Node Status
    private Boolean physical; // Whether the Node is Physical
}
