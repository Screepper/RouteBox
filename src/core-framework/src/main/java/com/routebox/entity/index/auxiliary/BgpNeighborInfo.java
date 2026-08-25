package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description BGP Neighbor Info
 * @Date 2025/6/5
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BgpNeighborInfo {
    private String neighborRouteId; // BGP Neighbor Route ID
    private Integer neighborAsNum; // BGP Neighbor AS Number
    private Integer messageReceivedNum; // BGP Message Received Number
    private Integer messageSentNum; // BGP Message Sent Number
    private String upDownTime; // BGP Neighbor Up/Down Time
    private String stateOrReceivedNum; // BGP Neighbor State or Received Number
    private Integer prefixSentNum; // BGP Prefix Sent Number
}
