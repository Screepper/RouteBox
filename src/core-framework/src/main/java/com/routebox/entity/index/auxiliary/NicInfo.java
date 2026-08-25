package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Network Card Info
 * @Date 2025/11/23
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NicInfo {
    private String name; // NIC Name
    private Integer receivePacketsRate; // Receive Packets Rate/sec
    private Integer transmitPacketsRate; // Transmit Packets Rate/sec
    private Float receiveKbRate; // Receive Traffic KB/sec
    private Float transmitKbRate; // Transmit Traffic KB/sec
}