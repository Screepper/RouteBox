from typing import Optional, List
from pydantic import BaseModel


class R:
    """standard response model"""
    code: int
    mes: str
    data: Optional[any] = None


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


class AttackableInfo(BaseModel):
    """attackable info"""

    class TopologySummary(BaseModel):
        """topology summary"""

        class NodeInfo(BaseModel):
            """node info"""
            nodeId: int
            name: str
            type: str
            status: str
            neighborVethInfo: List[str]
            pingableIp: str

        class LinkInfo(BaseModel):
            """link info"""
            linkId: int
            srcNodeId: int
            dstNodeId: int
            status: str

        sernesFrontendUrl: str
        runningNetworkId: int
        nodes: List[NodeInfo]
        links: List[LinkInfo]
        asNodeCount: int
        hostNodeCount: int
        linkCount: int

    class AttackType(BaseModel):
        """attack type"""

        belongTo: str
        type: str
        description: str
        serialNumber: int

    class RunningAttackSummary(BaseModel):
        """current attack event"""

        eventId: str
        startTime: int

    topologySummary: TopologySummary
    supportAttackTypes: List[AttackType]
    runningAttackEvent: Optional[RunningAttackSummary] = None


class Recommended(BaseModel):
    """recommended target info"""
    nodeId: int
    linkId: str
    score: float
    expectedImpactRatio: float
