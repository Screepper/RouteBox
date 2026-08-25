package com.routebox.entity.index;

import com.routebox.entity.index.auxiliary.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description Elasticsearch Entity: BGP Core Info Log
 * @Date 2025/5/29
**/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BgpLog extends IndexBase {
    private String routerId; // Router ID
    private Integer asNum; // AS Number
    private Integer ribEntriesNum; // RIB Entries Number
    private Integer peersNum; // Peers Number
    private Integer totalNeighborsNum; // Total Neighbors Number
    private List<BgpNeighborInfo> neighbors; // Neighbor Info from "show ip bgp summary"
    private List<BgpRouteEntry> routeEntries; // Route Table from "show ip bgp"
    private List<BgpNetworkDeclaration> networkIps; // Network IPs Declared from "show running-config"
    private List<BgpRouteMap> routeMaps; // Route Maps from "show running-config"
    private List<BgpNeighborRouteMap> neighborRouteMaps; // Neighbor Route Maps from "show running-config"
    private String runningConfig; // Running Config from "show running-config"
}
