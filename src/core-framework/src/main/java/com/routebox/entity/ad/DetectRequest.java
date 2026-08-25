package com.routebox.entity.ad;

import com.routebox.entity.index.SystemNetworkLog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @Description Request class for detecting anomaly
 * @Date 2025/11/23
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DetectRequest {
    private Map<Integer, SystemNetworkLog[]> currentMap; // Current period log map, key is nodeId, value is log array
    private Map<Integer, SystemNetworkLog[]> recentMap; // Recent period log map, key is nodeId, value is log array
    private Map<Integer, SystemNetworkLog[]> periodMap; // Period log map, key is nodeId, value is log array
}

