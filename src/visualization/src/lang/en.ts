export default {
  main: {
    title: "Routing Attack Emulation Platform",
    lang: "Lang",
  },
  leftmenu: {
    topo: "Topology",
    evaluate: "Evaluate",
    detect: "Detect",
  },
  pages: {
    default: {
      description: "Select a function from the left menu and start using",
    },
    sernes: {
      title: "SERNES Platform",
      navigate: "Navigate to SERNES",
      features: [
        {
          title: "Large-scale Emulation",
          description:
            "Based on lightweight container virtualization technology, it supports network topology emulation at the scale of ten thousand nodes, providing stable support for large-scale network simulation needs.",
        },
        {
          title: "Semi-physical Emulation",
          description:
            "Supports the connection of physical devices such as switches and routers, which can exchange traffic with the simulated network, helping to verify the functionality and performance of network devices.",
        },
        {
          title: "Custom Simulation Scenarios",
          description:
            "Provides a visualized interface and diversified configuration files, supporting custom network topology, virtual node configuration, etc., suitable for different network simulation needs.",
        },
        {
          title: "Diversified Simulation Data Collection",
          description:
            "Provides real-time monitoring and data collection interfaces, monitoring the traffic volume and node running status of the simulated network through visualization panels, facilitating subsequent high-level analysis.",
        },
        {
          title: "Real-time Interactive Operation",
          description:
            "Supports real-time network routing table debugging, route adjustment, node command execution and other operations for the running simulation network, realizing real-time network interaction.",
        },
        {
          title: "Efficient Environment Construction",
          description:
            "Provides efficient environment construction solutions, supporting rapid deployment and configuration, ensuring the stability and reliability of the simulation environment.",
        },
      ],
    },
    topoManage: {
      title: "Topology Manage",
      label: "Running Topology Info",
    },
    attack: {
      title: "Attack",
      launchAttack: "Launch Attack",
      attacker: "Attacker",
      selectAttacker: "Select Attacker",
      target: "Target",
      selectTarget: "Select Target",
      method: "Method",
      selectMethod: "Select Method",
      maxAttackNum: "Max Attack Number",
      attacking: "Attacking",
      duration: "Attack Duration",
      historyRecords: "Attack History Records",
      execute: "Execute Attack",
      revoke: "Revoke Attack",
    },
    dataMonitor: {
      title: "Data Monitor",
      gotoBtn: "VIsit My Dashboard",
    },
    realTimeDetect: {
      title: "Real-Time Detect",
      statusLabel: "Detection Status",
      messages: {
        start: "Detection Started",
        stop: "Detection Stopped (Please check the root cause analysis results in the history records)",
        checking: "Checking detection status...",
        waitForStart: "Waiting for detection to start...",
        empty: "No Detection Results",
      },
      resultsLabel: "Detection Results",
      results: {
        normal: "Node {node}: Normal",
        anomaly: "Node {node}: Anomaly Detected",
      },
      status: {
        detecting: "Detecting",
        waiting: "Waiting",
      },
    },
    detectHistories: {
      title: "Detect Histories",
      columns: {
        networkId: "Network ID",
        dataSource: "Data Source",
        timestamp: "Detect Finish Time",
        isAnomaly: "Is Anomaly",
        action: "Action",
      },
      status: {
        normal: "Normal",
        anomaly: "Anomaly",
      },
      buttons: {
        details: "Details",
      },
      dialog: {
        title: "Detection Details",
        attackType: "Attack Type",
        anomalyInfo: "Anomaly Information",
        topoInfo: "Topology Information",
        systemNetworkAnomaly: "SystemNetwork Anomaly",
        bgpAnomaly: "BGP Anomaly",
        routerAnomaly: "Router Anomaly",
        pingAnomaly: "Ping Anomaly",
        topoAnomaly: "Topology Anomaly",
        timeRange: "Time Range",
        evidence: "Evidence",
        possibilities: "Possibilities",
        participants: "Participants",
        attackers: "Attackers",
        victims: "Victims",
        directEvidence: "Direct Evidence",
        indirectEvidence: "Indirect Evidence",
        method: "Method",
        possibility: "Possibility",
        rationale: "Rationale",
      },
    },
  },
  common: {
    yes: "yes",
    no: "no",
    warn: "warn",
    cancel: "cancel",
    confirm: "confirm",
    save: "save",
    close: "close",
    edit: "edit",
    delete: "delete",
    add: "add",
    search: "search",
    reset: "reset",
    refresh: "refresh",
    back: "back",
    next: "next",
    submit: "submit",
    apply: "apply",
  },
};
