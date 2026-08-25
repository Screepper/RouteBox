from dataclasses import dataclass, field
from typing import List, Optional
from entity.nic_info import NicInfo


@dataclass
class SystemNetworkLog:
    logId: Optional[str] = ""
    eventId: Optional[str] = ""
    networkId: Optional[str] = 0
    nodeId: int = -1
    timestamp: int = 0
    cpuUseRatio: float = 0.0
    systemUseRatio: float = 0.0
    usedMemoryKb: int = 0
    memoryUseRatio: float = 0.0
    tps: int = 0
    ioReadKb: float = 0.0
    ioWriteKb: float = 0.0
    nics: List[NicInfo] = field(default_factory=list)
    totalReceivePacketsRate: int = 0
    totalTransmitPacketsRate: int = 0
    totalReceiveKbRate: float = 0.0
    totalTransmitKbRate: float = 0.0
