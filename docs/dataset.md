# Dataset

This repository currently has the following data resources open sourced.

This dataset share the same python libraries, you just need to install in /dataset, and you can run the python file in every subdirectory. Ensure you have installed Python 3.12+, and install the required libraries:

```bash
cd dataset

pip install -r requirements.txt

# or install by uv (recommended)
uv sync
```

Then configure your LLM endpoint in .env (BASE_URL, API_KEY, API_MODEL) for RCA evaluation.

## LLM-based Root Cause Analysis (RCA)

### Dataset Description

This dataset provides preprocessed prompts for evaluating Large Language Models (LLMs) in network security root cause analysis. It covers 10 distinct data source configurations (`S`, `SBP`, `SBRT`, `SBR`, `SBRPT`, `SBRPT_no_prompt`, `SBPT`, `SRT`, `SPT`, `ST`), (S=SystemNetwork Logs, B=BGP Logs, R=Router Logs, P=Ping Logs, T=Topology Logs) systematically isolating the contribution of different telemetry layers.

The dataset contains 268 pre-packaged anomaly events collected from October to November 2025 on a 100-node emulation topology. Each configuration directory contains timestamp-named `.txt` prompt files, directly consumable by LLMs. Ground-truth labels (`attackType`, `attackers`, `victims`, timeline) are provided in `groundtruth.json` for quantitative evaluation using a domain-specific scoring metric (Type, Attackers, Victims, Timeline).

We also provide the route leakage experiment dataset, which contains 4 file: groundtruth.json, question.txt, raw_question.txt (the question before LLM compression), and parsed.json (our RCA result).

The scoring.zip file contains our acutal test results that written in this paper, just for reference (if you do not need it, just ignore).

### Run RCA Evaluation

Fully reproducing the three core experiments (Overall RCA Performance, Log Importance, LLM Backbone Comparison) requires at least (268 \* 10 \* 1.5 / 60) = 67 hours depending on LLM response latency.

```bash
cd LLM_diagnose
# unzip the question.zip, and then run the main.py

# Parameters (-ds: data source configuration | -at: only test specific attack type | -ec: evaluation sample limit (useful if you just want to test a small number of samples))
python main.py -ds SBRPT -at "Prefix Hijacking" -ec 5

# 1. RCA Evaluation
# Default: Full evaluation on SBRPT configuration (268 samples) (~ 6.7 hours)
python main.py

# 2. Log Importance (e.g., test S only)
python main.py -ds S -ec 268

# 3. LLM Backbone (update .env)
python main.py -ds SBRPT
```

## SystemNetwork Anomaly Detection

### Dataset Description

This dataset focuses on monitoring the system network indicators in the network system. The data was collected from October to November 2025, including SystemNetwork Data of 8 AS nodes, divided into a training set (8 days of normal state) and a validation set (12 days with attacks), with a total data volume of 87MB and in CSV format (compressed in zip file with the data volume of 35MB).

### Run Baseline Evaluation

Run the METED baseline for 8 nodes only cost ~ 2 minutes.

```bash
cd system_network_anomaly_detection

# unzip the data.zip, and then run the main.py
python main.py
```

### Integrating a Custom Baseline

To evaluate your own algorithm (statistical or ML-based), simply implement a eval function that takes train_path and eval_path as input, and returns precision, recall, and f1 as output:

```python
if __name__ == "__main__":
    # 1. Import your evaluator and create instance
    evaluator = Algorithm()
    results = []
    # 2. Evaluate each node
    for node_id in range(1, 9):
        train_path = f"data/train/{node_id}.csv"
        eval_path = f"data/eval/{node_id}.csv"
        # Implement eval function, if your algorithm is ML-based, you need to train the model first, and then predict the labels for the eval_path.
        # We provide the dataset_parse_util for parsing the csv file, including get field values, fetch data dimension, and construct Torch.Tensor dataset. But, you need to manually install torch if you will use the generate_data function.
        precision, recall, f1 = evaluator.eval(train_path, eval_path)
        results.append((node_id, precision, recall, f1))
```

## Multi Modal Data

This dataset provides comprehensive multi-modal telemetry data from a 100-node network system emulation (50 Autonomous System nodes and 50 Host nodes), collected during October–November 2025. The raw dataset comprises 224GB of JSON-formatted logs across six categories: `SystemNetwork`, `BGP`, `Router`, `Ping`, `Topology`, and `Attack`. Due to the large size of this dataset and considering anonymity issues, we will not release it temporarily.

Each log type captures distinct aspects of network system behavior:

- **SystemNetwork**: Resource-level metrics (CPU, memory, disk I/O, NIC traffic) at 1-second granularity.
- **BGP**: Protocol-level state including neighbor sessions, RIB entries, and advertised prefixes, sampled every 15 seconds.
- **Router**: Configuration change history and command execution logs in real-time.
- **Ping**: End-to-end reachability and latency measurements between node pairs, collected every 10 seconds.
- **Topology**: Global node/link status snapshots and adjacency information, updated every 15 seconds.
- **Attack**: Ground-truth annotations of injected attack events (used for evaluation and auditing, not for detection).

The dataset is organized hierarchically by node type and log category, with hourly JSON shards for efficient partial loading. All logs are timestamped with Unix epoch (10-digit) for precise temporal alignment across modalities.

### Directory Structure

```
multi_modal_routing_system_data/
├── global/ # global logs
│   ├── Topology/ # Topology logs (Structured by yyMMdd/hh-hh.json)
│   │   ├── 20251010/
│   │   │   ├── 18-19.json # Logs start at 2025-10-10 18:00
│   │   │   ├── 19-20.json # Topology logs at 19:00 to 20:00
│   │   │   ├── ...
│   │   │   └── 23-24.json # Topology logs at 23:00 to 00:00
│   │   ├── .../
│   │   └── 20251101/
│   │       ├── 00-01.json
│   │       ├── 01-02.json
│   │       ├── ...
│   │       └── 20-21.json # Logs stop at 2025-11-01 21:00
│   └── Attack/ # Attack logs
│       └── attack.json # attack records (Ground Truth)
└── nodes/ # node-specific logs (Structured by NodeInfo/LogType/yyMMdd/hh-hh.json)
    ├── Node1 (AS)/ # Node 1, AS nodes contain SystemNetwork, BGP, Router, Ping logs
    │   ├── SystemNetwork/
    │   │   ├── 20251010/
    │   │   │   ├── 18-19.json
    │   │   │   ├── 19-20.json
    │   │   │   ├── ...
    │   │   │   └── 23-24.json
    │   │   ├── .../
    │   │   └── 20251101/
    │   │       ├── 00-01.json
    │   │       ├── 01-02.json
    │   │       ├── ...
    │   │       └── 20-21.json
    │   ├── BGP/
    │   ├── Router/
    │   └── Ping/
    ├── .../ # Node 2 to Node 50 (AS nodes)
    ├── Node51 (Host)/ # Node 51, Host nodes contain SystemNetwork, Ping logs
    │   ├── SystemNetwork/
    │   │   ├── 20251010/
    │   │   │   ├── 18-19.json
    │   │   │   ├── 19-20.json
    │   │   │   └── ...
    │   │   └── .../
    │   └── Ping/
    └── .../ # Node 52 to Node 100 (Host nodes)
```

### Data Sample

#### Topology Log

```json
{
  "eventId": null, // Recorded the attack event ID if this log is recorded during the triggering of an attack event
  "nodeId": -1, // -1 means global log
  "timestamp": 1760094175,
  "adjacency": [
    {
      "nodeId": 1,
      "adjacentNodeIds": [2, 18, 51]
    },
    // ...
    {
      "nodeId": 100,
      "adjacentNodeIds": [50]
    }
  ],
  "nodeHealthyRatio": 1.0,
  "linkHealthyRatio": 1.0,
  "nodes": [
    {
      "id": 1,
      "type": "as",
      "status": "UP",
      "physical": false
    },
    // ...
    {
      "id": 100,
      "type": "host",
      "status": "UP",
      "physical": false
    }
  ],
  "links": [
    {
      "id": "1",
      "sourceId": 3,
      "targetId": 9,
      "status": "UP"
    },
    // ...
    {
      "id": "123",
      "sourceId": 50,
      "targetId": 100,
      "status": "UP"
    }
  ]
}
```

#### SystemNetwork Log

```json
{
  "eventId": null,
  "nodeId": 1,
  "timestamp": 1760094000,
  "cpuUseRatio": 2.9573,
  "systemUseRatio": 8.3567,
  "usedMemoryKb": 27164,
  "memoryUseRatio": 0.000662,
  "tps": 0,
  "ioWriteKb": 0.0,
  "nics": [
    {
      "name": "eth0",
      "receivePacketsRate": 62,
      "transmitPacketsRate": 63,
      "receiveKbRate": 211.34961,
      "transmitKbRate": 211.49414
    },
    {
      "name": "eth1",
      "receivePacketsRate": 61,
      "transmitPacketsRate": 65,
      "receiveKbRate": 153.64941,
      "transmitKbRate": 154.24023
    },
    {
      "name": "eth2",
      "receivePacketsRate": 92,
      "transmitPacketsRate": 87,
      "receiveKbRate": 273.16602,
      "transmitKbRate": 272.43066
    }
  ],
  "totalReceivePacketsRate": 215,
  "totalTransmitPacketsRate": 215,
  "totalReceiveKbRate": 638.16504,
  "totalTransmitKbRate": 638.16504
}
```

#### BGP Log

```json
{
  "eventId": null,
  "nodeId": 1,
  "timestamp": 1760094121,
  "routerId": "10.0.1.37",
  "asNum": 1,
  "ribEntriesNum": 99,
  "peersNum": 2,
  "totalNeighborsNum": 2,
  "neighbors": [
    {
      "neighborRouteId": "10.0.0.54",
      "neighborAsNum": 2,
      "messageReceivedNum": 145,
      "messageSentNum": 147,
      "upDownTime": "01:24:02",
      "stateOrReceivedNum": "42",
      "prefixSentNum": 50
    },
    {
      "neighborRouteId": "10.0.0.90",
      "neighborAsNum": 18,
      "messageReceivedNum": 148,
      "messageSentNum": 147,
      "upDownTime": "01:24:02",
      "stateOrReceivedNum": "41",
      "prefixSentNum": 50
    }
  ],
  "routeEntries": [
    {
      "ipPrefix": "10.0.1.36/30",
      "nextHop": "0.0.0.0",
      "weight": 32768,
      "path": "i",
      "best": true
    },
    {
      "ipPrefix": "10.0.1.40/30",
      "nextHop": "10.0.0.54",
      "weight": 0,
      "path": "2 i",
      "best": true
    },
    // ...
    {
      "ipPrefix": "10.0.1.232/30",
      "nextHop": "10.0.0.90",
      "weight": 0,
      "path": "18 27 50 i",
      "best": true
    }
  ],
  "networkIps": [
    {
      "ipPrefix": "10.0.1.36/30",
      "routeMapName": null,
      "mask": null
    },
    {
      "ipPrefix": "fd00::124/126",
      "routeMapName": null,
      "mask": null
    }
  ],
  "routeMaps": [],
  "neighborRouteMaps": [],
  "runningConfig": "Building configuration...\n\nCurrent configuration:\n!\nfrr version 8.4.1_git\nfrr defaults traditional\nhostname node3462\nlog file /usr/routebox/logs/realtime/frr.log informational\nlog commands\n!\nrouter bgp 1\n no bgp ebgp-requires-policy\n no bgp network import-check\n neighbor 10.0.0.54 remote-as 2\n neighbor 10.0.0.90 remote-as 18\n !\n address-family ipv4 unicast\n  network 10.0.1.36/30\n  neighbor 10.0.0.54 next-hop-self\n  neighbor 10.0.0.90 next-hop-self\n exit-address-family\n !\n address-family ipv6 unicast\n  network fd00::124/126\n exit-address-family\nexit\n!\nrpki\nexit\n!\nend"
}
```

#### Router Log

```json
{
  "eventId": null,
  "nodeId": 1,
  "timestamp": 1760115699,
  "histories": [
    {
      "time": 1760115670,
      "command": "enable"
    },
    {
      "time": 1760115670,
      "command": "show ip bgp summary"
    },
    // ...
    {
      "time": 1760115728,
      "command": "echo PING"
    }
  ]
}
```

#### Ping Log

```json
{
  "eventId": null,
  "nodeId": 1,
  "timestamp": 1760112014,
  "pingNodeId": 32,
  "pingIp": "10.0.1.161",
  "delayMs": 0.472
}
```

#### Attack Log

```json
{
  "eventId": "16aaf580m3gFONhG",
  "nodeId": -1,
  "timestamp": 1761316894,
  "endTimestamp": 1761317663,
  "attackType": "Prefix Hijacking",
  "attackers": [8],
  "victims": [5]
}
```

### How to Use

We provide a unified extraction script `main.py` to retrieve logs within arbitrary time ranges and for specified node/log-type combinations.

```bash
# download and unzip multi_modal_routing_system_data.zip first

# Basic usage: extract SystemNetwork logs for AS nodes 2,5,10 from a specific timestamp onwards
python main.py -st "2025-10-15 14:30:00" -lt S -ids 2,5,10

# Extract multiple log types (S+B+R+P+T) for a single host node within a time window
python main.py -st 1760094000 -et 1760097600 -lt SBRPT -ids 57

# Extract only global Topology and attack records (no node-specific logs)
python main.py -st "2025-10-10 00:00:00" -et "2025-10-11 00:00:00" -lt T

# Extract all available logs for mixed AS/Host nodes
python main.py -st 1760090400 -lt SBRPT -ids 1,25,51,75,100
```

## BGP Update Messages

This dataset provides 290k BGP update messages (`bgp_update.json`) from 50 AS nodes in a 100-node topology, as well as an attack log file (`attack.json`). The specific generation logic of this dataset is: every half hour, prefix hijacking (attack event), sub-prefix hijacking (attack event), and node down/up (trigger normal re-declaration of AS nodes) are randomly triggered in AS nodes, with each event lasting about 10 minutes.

We conducted the evaluation experiment in our paper based on the BGP anomaly detection algorithm evaluation platform [RADBench](https://github.com/DoDo58-tech/RADBench). Due to the complex setup of the experimental environment and the intricate configuration of each method, we only provided the data resources used for the evaluation, and a reference script `eval_artemis.py`. If you wish to complete all the relevant experiments, please refer to the official documentation of RADBench.
