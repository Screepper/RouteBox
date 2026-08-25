package com.routebox.entity.topology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Node Veth IP Info
 * @Date 2026/1/17
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeVethIpInfo {
    private String fromVeth;
    private String toVeth;
    private String fromIp;
    private String toIp;
}
