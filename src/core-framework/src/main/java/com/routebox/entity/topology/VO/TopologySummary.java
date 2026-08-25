package com.routebox.entity.topology.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @Description TopologyVO
 * @Date 2025/12/12
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopologySummary implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String sernesFrontendUrl;
    private Integer runningNetworkId;
    private List<NodeInfo> nodes;
    private List<LinkInfo> links;
    private Integer asNodeCount;
    private Integer hostNodeCount;
    private Integer linkCount;
}
