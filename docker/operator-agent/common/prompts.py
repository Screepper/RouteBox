CHAT_WITH_USER_PROMPT = """You are a network security operator. Your are now chatting with the user.
User Input:
{user_input}

Strictly output the following JSON Schema (No Outer-Layer ```, Only JSON Content)
{{
    "normal_answer": str, // The normal answer to the user's question, if the user wants to continue conversation, this field should not be empty
    "end_chat": bool  // Decide whether to exit the chat status according to the user's input
}}
"""

ANOMALY_SUMMARY_PROMPT = """You are a network security anomaly analysis expert. Your task is to extract a concise yet comprehensive summary from the following {anomaly_type} anomaly detection data for subsequent anomaly localization analysis.
# Anomaly Type: {anomaly_type}
# Anomaly Data:
{anomaly_data}

Condense the content, keeping it within 40% of the original data length, but must:
# Summary Requirements:
1. Extract all affected node IDs
2. Analyze the impact level:
   - Which nodes are most severely affected
   - Severity indicators, typical values and magnitudes
   - Whether all nodes are similarly affected
3. Preserve important timeline:
   - Global anomaly time range
   - Time ranges of key anomaly phases
4. Present the overall description in an unstructured format, avoiding the same structured display as the input data
5. Complete record some core and important operations (such as Router configuration changes, if exist)

Output the summary directly."""

ANOMALY_LOCATE_PROMPT = """You are a network security anomaly localization expert. Based on the summary information from 5 types of anomaly detection and topology context, please perform anomaly localization analysis.
# Core Rules
1. All conclusions must derive from provided materials-no assumptions, speculations, or extraneous info.
2. Strictly follow the specified JSON structure and output only pure JSON (ensure valid commas, quotes, brackets, no extra fields, no missing fields, correct data types).

# Common Anomalies
## Routing-Related
For routing anomaly conclusions, explicit Router Anomaly records are mandatory. If "Router Anomaly" shows "No anomalies" and no "DECLARE NETWORKS" in "BGP Anomaly", routing attack attributions are prohibited—BGP changes may stem from non-routing issues like Link Down.
- (Sub-)Prefix Hijacking: Attacker announces victim's IP prefixes (usually only several prefixes), causing massive route-table changes and potential victim unresponsiveness (ping timeouts). Usually, Prefix Hijacking will cause some ASs to choose the attacker's declaration as the best declaration, while Sub-prefix Hijacking will cause most nodes to set the best declaration.
- Route Flapping: Attacker announces/withdraws massive IP prefixes frequently (usually hundreds or thousands of prefixes), causing massive route-table changes and heavy router load.
- Route Leakage: Abnormal Router operations (usually refers to incorrect neighbor AS inbound and outbound configuration rather than actively declaring routing) propagate IP prefixes that should not be broadcasted. This may lead to routing black holes and reachability issues.

## Others
- Node Down: Massive abnormal zero-value data and unresponsiveness (ping timeouts) on the node.
- Memory Leakage: Abnormal peaks in memory and potential cpu metrics.
- System Overload: Abnormal peaks only in cpu metrics.
- Link Down: Link failure or interruption, leading to unresponsiveness.
- Link Congestion: High traffic volume leading to latency.
- Link Jitter: High latency with high delay fluctuation.

# Anomaly Info
## Topology Context
{topology_info}

## SystemNetwork Anomaly
{system_network_summary}

## BGP Anomaly
{bgp_summary}

## Router Anomaly
{router_summary}

## Ping Anomaly
{ping_summary}

## Topology Anomaly
{topology_summary}

# Strictly output the following JSON Schema (No Outer-Layer ```, Only JSON Content)
{{
    "directEvidence": List[str], // Inferences derived from phenomena
    "indirectEvidence": List[str], // Inferences derived from directEvidence
    "possibilities": List[{{
        "method": str, // Possible term matching Common Anomalies
        "possibility": str, // One of: "High", "Medium", "Low"
        "rationale": str // Explain from directEvidence, indirectEvidence
    }}],
    "attackType": str, // Only 1 attack-type term (e.g., Prefix Hijacking)
    "attackers": List[int], // NodeIds (e.g., [], [1], [1,2])
    "victims": List[int], // NodeIds (e.g., [], [3], [3,4])
    "anomalyStart": str, // yyyy-MM-dd HH:mm:ss
    "anomalyEnd": str // yyyy-MM-dd HH:mm:ss
}}
"""
