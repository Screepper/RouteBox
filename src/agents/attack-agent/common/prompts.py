CHAT_WITH_USER_PROMPT = """You are a network security operator. Your are now chatting with the user.
User Input:
{user_input}

Strictly output the following JSON Schema (No Outer-Layer ```, Only JSON Content)
{{
    "normal_answer": str, // The normal answer to the user's question, if the user wants to continue conversation, this field should not be empty
    "end_chat": bool  // Decide whether to exit the chat status according to the user's input
}}
"""

ATTACK_PREPARE_PROMPT = """You are an expert in routing security and attack injection, and now you are in the Attack Preparation phase. Your task is to analyze the current network state and formulate an attack plan.
Topology state and attackable information:
{attackable_info}

Your decision process:
1. Review available attackable info to understand the attack types and their characteristics
2. Consider which attack type would be effective given the current network state
3. Determine the attack parameters
4. Provide a detailed rationale for your decision

Strictly output the following JSON Schema (No Outer-Layer ```, Only JSON Content)
{{
    "attack_type": str, // The selected attack type from the available attack types
    "belong_to": Literal["BGP-like", "Node-like", "Link-like"], // The attack type belongs to which category
    "serial_number": int, // The serial number of the attack type
    "attacker": int, // attackerNodeId if the attack type requires a specific attacker, otherwise None
    "victims": List[int | str], // List of target ids (nodeId (int) or linkId (str), but not both)
    "rationale": str, // Explanation of why this attack type and parameters were chosen
    "fetch_recommendation": bool, // Whether to fetch recommended attack targets from the backend before executing the attack
}}
"""

ATTACK_EXECUTE_PROMPT = """You are an expert in routing security and attack injection, and now you are in the Attack Execution phase.
Attack Plan: {attack_plan}

Your task is to execute this attack by calling the provided tool with the appropriate parameters.

Strictly output the following JSON Schema (No Outer-Layer ```, Only JSON Content)
{{
    "success": bool, // Whether the attack execution is successful
    "attack_id": str, // Attack ID if the execution is successful
    "attack_type": str, // Attack type if the execution is successful
    "reason": str // Reason for the execution result (adopt only when the execution is unsuccessful)"
}}
"""

ANOMALY_SUMMARY_PROMPT = """You are a network security anomaly analysis expert. Your task is to extract a concise yet comprehensive summary from the following {anomaly_type} anomaly detection data for subsequent anomaly localization analysis.
# Anomaly Type: {anomaly_type}
# Anomaly Data:
{anomaly_data}

Condense the content, keeping it within 20% of the original data length, but must:
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

Output the summary directly."""

EXPERIENCE_FUSION_PROMPT = """You are an expert in routing security and attack strategy optimization. Your task is to fuse a new attack experience with historical experiences to create an optimized attack strategy library.

## New Attack Experience (Abstract)
{attack_summary}

## Historical Attack Experiences (Markdown)
{historical_experiences}

Please perform experience fusion by:

1. **Identifying Patterns**: Find common patterns, successful strategies, and recurring issues across the experiences

2. **Updating Attack Strategy Library**:
   - For each attack type mentioned in the new experience, update or add a strategy entry
   - Highlight what makes certain attacks more effective
   - Document the relationship between attack parameters and outcomes

3. **Generating Updated Experience Document**:
   Produce a markdown document with the following structure:

# Attack Experience Library

## Strategy Overview
[Brief overview of the attack strategy library]

## Attack Type Strategies

### [Attack Type 1]
- **Characteristics**: [key characteristics]
- **Optimal Conditions**: [when this attack works best]
- **Effectiveness**: [typical impact and success rate]
- **Attack Parameters**: [recommended settings]
- **Historical Success Rate**: [based on experience]
- **Lessons**: [key learnings from historical executions]

### [Attack Type 2]
[... similar structure ...]

## Improvement Trends
[How attack strategies have evolved over time based on experience]

## Recommendations
- **For High Impact**: [recommended attack strategies for maximum impact]
- **For Stealth**: [recommended attack strategies for avoiding detection]
- **For Persistence**: [recommended attack strategies for sustained anomalies]

Format the output as a clean markdown document that can be directly saved and referenced for future attack planning.

Strictly output the following JSON Schema (No Outer-Layer ```, Only JSON Content)
{{
    strategy_library: str, // The updated strategy library in markdown format
    new_patterns_identified: List[str], // New patterns identified from this experience
    improvements_applied: List[str], // Improvements applied to existing strategies
}}
"""
