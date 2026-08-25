package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description BGP Neighbor Route Map Info
 * @Date 2025/11/23
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BgpNeighborRouteMap {
    private String neighborIp; // BGP Neighbor ID
    private String routeMapName; // BGP Neighbor Route Map Name
    private String direction; // in/out
}