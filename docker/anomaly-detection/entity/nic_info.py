from dataclasses import dataclass


@dataclass
class NicInfo:
    name: str = ""
    receivePacketsRate: int = 0
    transmitPacketsRate: int = 0
    receiveKbRate: float = 0.0
    transmitKbRate: float = 0.0
