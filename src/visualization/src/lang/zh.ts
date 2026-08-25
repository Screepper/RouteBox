export default {
  main: {
    title: "路由攻击模拟平台",
    lang: "语言",
  },
  leftmenu: {
    topo: "拓扑",
    evaluate: "评估",
    detect: "检测",
  },
  pages: {
    default: {
      description: "从左侧菜单选择一个功能并开始使用",
    },
    sernes: {
      title: "仿真平台",
      navigate: "导航到SERNES",
      features: [
        {
          title: "大规模仿真能力",
          description:
            "依托轻量级容器虚拟化技术，支持万级节点的网络拓扑仿真能力，为大规模网络模拟需求提供稳定支撑。",
        },
        {
          title: "虚实结合仿真",
          description:
            "支持接入交换机、路由器等物理设备，可与仿真网络进行流量交互，有助于验证实物设备的功能和性能表现。",
        },
        {
          title: "灵活的仿真场景定制",
          description:
            "提供图形化界面和多样化配置文件，支持网络拓扑、虚拟节点配置等自定义，适用于不同网络仿真需求。",
        },
        {
          title: "多元化的仿真数据采集",
          description:
            "提供实时监控界面和数据采集接口，监控仿真网络的流量包流量和节点运行日志，方便后续的高层分析。",
        },
        {
          title: "实时交互操作",
          description:
            "支持对运行中的仿真网络进行路由表调试、路由调层、节点命令行交互等操作，实现实时网络交互。",
        },
        {
          title: "高效的环境搭建",
          description:
            "提供高效的环境搭建方案，支持快速部署和配置，确保仿真环境的稳定性和可靠性。",
        },
      ],
    },
    topoManage: {
      title: "拓扑管理",
      label: "运行中拓扑信息",
    },
    attack: {
      title: "攻击注入",
      launchAttack: "发动攻击",
      attacker: "攻击者",
      selectAttacker: "选择攻击者",
      target: "攻击目标",
      selectTarget: "选择攻击目标",
      method: "攻击方式",
      selectMethod: "选择攻击方式",
      maxAttackNum: "最大攻击数量",
      attacking: "攻击进行中",
      duration: "攻击持续时间",
      historyRecords: "历史攻击记录",
      execute: "执行攻击",
      revoke: "撤销攻击",
    },
    dataMonitor: {
      title: "数据监控",
      gotoBtn: "访问我的仪表盘",
    },
    realTimeDetect: {
      title: "实时检测",
      statusLabel: "检测状态",
      messages: {
        start: "检测开始",
        stop: "检测结束（大模型根因定位结果请与历史检测记录中查看）",
        checking: "正在检查检测状态...",
        waitForStart: "等待检测开始...",
        empty: "暂无检测结果",
      },
      resultsLabel: "检测结果",
      results: {
        normal: "节点 {node}：正常",
        anomaly: "节点 {node}：检测到异常",
      },
      status: {
        detecting: "检测中",
        waiting: "等待中",
      }
    },
    detectHistories: {
      title: "检测历史",
      columns: {
        networkId: "网络ID",
        dataSource: "数据源",
        timestamp: "检测完成时间",
        isAnomaly: "是否异常",
        action: "操作",
      },
      status: {
        normal: "正常",
        anomaly: "异常",
      },
      buttons: {
        details: "详情",
      },
      dialog: {
        title: "检测详情",
        attackType: "攻击类型",
        anomalyInfo: "异常信息",
        topoInfo: "拓扑信息",
        systemNetworkAnomaly: "系统网络异常",
        bgpAnomaly: "BGP异常",
        routerAnomaly: "路由器异常",
        pingAnomaly: "探测异常",
        topoAnomaly: "拓扑异常",
        timeRange: "时间范围",
        evidence: "证据",
        possibilities: "可能性",
        participants: "参与者",
        attackers: "攻击者",
        victims: "受害者",
        directEvidence: "直接证据",
        indirectEvidence: "间接证据",
        method: "方法",
        possibility: "可能性",
        rationale: "理由",
      },
    },
  },
  common: {
    yes: "是",
    no: "否",
    cancel: "取消",
    warn: "注意",
    confirm: "确定",
    save: "保存",
    close: "关闭",
    edit: "编辑",
    delete: "删除",
    add: "添加",
    search: "搜索",
    reset: "重置",
    refresh: "刷新",
    back: "返回",
    next: "下一步",
    submit: "提交",
    apply: "应用",
  },
};
