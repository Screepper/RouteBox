package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Router Command Execution Info
 * @Date 2025/7/6
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VtyshInfo {
    private Long time; // Command Execution Timestamp
    private String command; // Command Executed
}
