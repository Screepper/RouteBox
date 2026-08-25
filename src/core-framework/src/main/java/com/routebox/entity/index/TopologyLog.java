package com.routebox.entity.index;

import com.routebox.entity.index.auxiliary.AdjacencyInfo;
import com.routebox.entity.index.auxiliary.TopologyLinkInfo;
import com.routebox.entity.index.auxiliary.TopologyNodeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description Elasticsearch Entity: Topology Log
 * @Date 2025/5/27
**/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopologyLog extends IndexBase {
    private List<AdjacencyInfo> adjacency; // Adjacency Info
    private Float nodeHealthyRatio; // Node Healthy Ratio
    private Float linkHealthyRatio; // Link Healthy Ratio
    private List<TopologyNodeInfo> nodes; // Node Info
    private List<TopologyLinkInfo> links; // Link Info
}
