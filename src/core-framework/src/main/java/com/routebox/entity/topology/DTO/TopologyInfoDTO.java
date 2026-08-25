package com.routebox.entity.topology.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description SERNES Returned Topology Info
 * @Date 2025/3/27
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopologyInfoDTO{
    @JsonProperty("network_id")
    private Integer networkId;
    private List<NodeDTO> nodes; // NodeDTOs
    private List<LinkDTO> links; // LinkDTOs
    private List<String> bridges;
    private List<String> groups;
}
