package com.routebox.entity.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Elasticsearch Entity: Base Info Log
 * @Date 2025/5/7
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IndexBase {
    private String logId; // to be corrected: id
    private String eventId; // Active attack eventId when the log is generated
    private Integer networkId; // Network ID
    private Integer nodeId; // NodeId (-1: Global Log, e.g. AttackLog and TopologyLog)
    private Long timestamp; // 10-digit timestamp
}