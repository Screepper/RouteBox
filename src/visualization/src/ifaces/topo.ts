interface Feature {
  title: string;
  description: string;
  icon: string;
}

interface NodeInfo {
  nodeId: number;
  name: string;
  type: string;
  status: string;
  neighborVethInfo: string[];
  pingableIp: string;
}
interface LinkInfo {
  linkId: string;
  srcNodeId: number;
  dstNodeId: number;
  status: string;
}

interface TopologyInfo {
  sernesFrontendUrl: string;
  runningNetworkId: number;
  nodes: NodeInfo[];
  links: LinkInfo[];
  asNodeCount: number;
  hostNodeCount: number;
  linkCount: number;
}

export type { Feature, NodeInfo, LinkInfo, TopologyInfo };
