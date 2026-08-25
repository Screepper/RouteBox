package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Topology Link Info
 * @Date 2025/3/27
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopologyLinkInfo {
    private String linkId; // LinkId
    private Integer sourceNodeId; // Link source nodeId
    private Integer targetNodeId; // Link target nodeId
    private String status; // Link status
}
