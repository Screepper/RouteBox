import type { TopologyInfo } from "./topo";

interface RunningAttackSummary {
    eventId: string;
    startTime: number;
}

interface AttackType {
    belongTo: string;
    type: string;
    description: string;
    serialNumber: number;
}

interface AttackParam {
  topologySummary: TopologyInfo;
  supportAttackTypes: AttackType[];
  runningAttackSummary: RunningAttackSummary;
}

interface AttackLog {
  logId: string;
  eventId: string;
  networkId: number;
  nodeId: number;
  timestamp: number;
  endTimestamp: number;
  attackType: string;
  attackers: number[];
  victims: number[];
  attackProcess: string;
}

interface Recommended {
  nodeId?: number;
  linkId?: string;
  score: number;
  expectedImpactRatio: number;
}

export type { RunningAttackSummary, AttackParam, AttackLog, AttackType, Recommended };
