interface RdMes {
  stage: number;
  mes: string;
  time: string;
}

interface ParsedPossibility {
  method: string; // Method name
  possibility: string; // Possibility of the method
  rationale: string; // Rationale for the possibility
}

interface DetectResultLog {
  logId: string; // Ignore Here
  eventId: string; // Ignore Here
  networkId: number; // Network ID
  nodeId: number; // Ignore Here
  timestamp: number; // Detect end timestamp

  // Base info
  dataSource: string; // Data source for detection
  isAnomaly: boolean; // Whether the detection result is anomalous

  // Anomaly Info (All Ignore Here)
  topologyInfo: string; // Topology information
  systemNetworkAnomaly: string; // SystemNetwork anomaly information
  bgpAnomaly: string; // BGP anomaly information
  routerAnomaly: string; // Router anomaly information
  pingAnomaly: string; // Ping anomaly information
  topologyAnomaly: string; // Topology anomaly information

  // LLM-Parsed Info (All Ignore Here)
  directEvidence: string[]; // Direct evidence
  indirectEvidence: string[]; // Indirect evidence
  possibilities: ParsedPossibility[]; // Possibility of each method
  attackType: string; // Conclusion type, e.g. "attack"
  attackers: number[]; // Attacker nodeIds
  victims: number[]; // Victim nodeIds
  anomalyStart: string; // Anomaly start timestamp
  anomalyEnd: string; // Anomaly end timestamp
}

export type { RdMes, ParsedPossibility, DetectResultLog };
