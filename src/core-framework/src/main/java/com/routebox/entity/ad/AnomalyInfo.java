package com.routebox.entity.ad;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Exception field information class, which contains which field of the node is abnormal and related description
 * @Date 2025/7/26
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnomalyInfo {
    private Integer nodeId; // NodeId
    private Long startTime; // Exception start time timestamp
    private Long endTime;   // Exception end time timestamp
    private String fieldName; // Exception field name
    private String description; // Exception description
}
