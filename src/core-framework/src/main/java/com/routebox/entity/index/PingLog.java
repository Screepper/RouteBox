package com.routebox.entity.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @Description Elasticsearch Entity: Node-Ping Log
 * @Date 2025/6/4
**/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PingLog extends IndexBase {
    private Integer pingNodeId; // Ping target nodeId
    private String pingIp; // Ping target IP
    private Float delayMs; // Ping delay in milliseconds
}
