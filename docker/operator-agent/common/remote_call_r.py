from typing import Optional, List


class R:
    """standard response model"""

    code: int
    mes: str
    data: Optional[dict] = None


class IndexBase:
    """base index model for Elasticsearch"""

    logId: str
    eventId: Optional[str]
    networkId: Optional[int]
    nodeId: int
    timestamp: int


class DetectResultLog(IndexBase):
    """anomaly detection result"""

    def __init__(self):
        self.isAnomaly = False

    class ParsedPossibility:
        """parsed anomaly possibility"""

        method: str
        possibility: str
        rationale: str

        def to_dict(self):
            return {
                "method": self.method,
                "possibility": self.possibility,
                "rationale": self.rationale,
            }

    dataSource: str
    isAnomaly: bool

    topologyInfo: Optional[str] = None
    systemNetworkAnomaly: Optional[str] = None
    bgpAnomaly: Optional[str] = None
    routerAnomaly: Optional[str] = None
    pingAnomaly: Optional[str] = None
    topologyAnomaly: Optional[str] = None

    directEvidence: Optional[List[str]] = None
    indirectEvidence: Optional[List[str]] = None
    possibilities: Optional[List[ParsedPossibility]] = None
    attackType: Optional[str] = None
    attackers: Optional[List[int]] = None
    victims: Optional[List[int]] = None
    anomalyStart: Optional[str] = None
    anomalyEnd: Optional[str] = None


class ForceRevokeInfo:
    """force revoke attack info"""

    attackType: Optional[str] = None
    attackerName: Optional[str] = None
    victimNames: Optional[List[str]] = None
