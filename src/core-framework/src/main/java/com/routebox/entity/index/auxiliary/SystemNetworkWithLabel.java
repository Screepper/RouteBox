package com.routebox.entity.index.auxiliary;

import com.routebox.entity.index.SystemNetworkLog;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Description System Network Log with Label
 * @Date 2025/11/23
**/
@EqualsAndHashCode(callSuper = true)
@Data
public class SystemNetworkWithLabel extends SystemNetworkLog {
    private int label; // Anomaly Label
}