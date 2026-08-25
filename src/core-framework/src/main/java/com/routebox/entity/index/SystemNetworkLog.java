package com.routebox.entity.index;

import com.routebox.entity.index.auxiliary.NicInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description Elasticsearch Entity: System Network Log
 * @Date 2025/6/6
**/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemNetworkLog extends IndexBase {
    private Float cpuUseRatio; // User CPU Usage Ratio （AVG）
    private Float systemUseRatio; // System CPU Usage Ratio （AVG）
    private Long usedMemoryKb; // Used Memory KB （AVG）
    private Float memoryUseRatio; // Memory Usage Ratio （AVG）
    private Integer tps; // Read/Write Times/s （SUM）
    private Float ioWriteKb; // Write Traffic kb/s （SUM）
    private List<NicInfo> nics; // NIC Info （SUM）
    private Integer totalReceivePacketsRate; // Total Receive Packets/s （SUM）
    private Integer totalTransmitPacketsRate; // Total Transmit Packets/s （SUM）
    private Float totalReceiveKbRate; // Total Receive Traffic kb/s （SUM）
    private Float totalTransmitKbRate; // Total Transmit Traffic kb/s （SUM）
}
