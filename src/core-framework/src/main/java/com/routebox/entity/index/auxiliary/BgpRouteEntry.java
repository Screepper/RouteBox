package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description BGP Route Entry Info
 * @Date 2025/3/30
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BgpRouteEntry {
    private String ipPrefix; // BGP Route IP Prefix
    private String nextHop; // BGP Route Next Hop
    private int weight; // BGP Route Weight
    private String path; // BGP Route Path
    private boolean isBest; // Whether it is the best route for this IP prefix
}
