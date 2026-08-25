from datetime import datetime
from langgraph.graph import StateGraph
from langgraph.checkpoint.memory import InMemorySaver
from langchain_core.messages import HumanMessage
from asyncio import sleep

from .state import OperatorAgentState
from common.remote_call_r import DetectResultLog, ForceRevokeInfo
from common.agent_logger import logger
from common.structure_outputs import EndChat, AnomalyLocate
from common.prompts import (
    CHAT_WITH_USER_PROMPT,
    ANOMALY_SUMMARY_PROMPT,
    ANOMALY_LOCATE_PROMPT,
)
from tools.info_tools import (
    _get_latest_detection_record,
    _update_latest_detect_record,
)
from tools.memory_tools import save_short_term_memory
from tools.operation_tools import _force_revoke
from tools.tool_util import strongly_call
from config import (
    MAX_REPAIR_DURATION,
    BASE_INTERVAL,
    MAX_REPAIR_ATTEMPTS,
    WAIT_DURATION,
    INTERRUPT_TIMEOUT,
)
from ws.queue_util import MesQueue


async def user_interaction_node(state: OperatorAgentState) -> OperatorAgentState:
    """the self-running agent was interrupted by user's input, and now it's in user interaction phase"""
    user_input = state["user_input"]

    if state["interrupt_start_time"] is None:
        state["interrupt_start_time"] = datetime.now()
        state["breakpoint_state"] = {
            "current_operation_start_time": state["current_operation_start_time"],
            "anomaly_start_time": state["anomaly_start_time"],
            "repair_attempt_count": state["repair_attempt_count"],
            "short_term_memory": state["short_term_memory"],
            "next_path": state["next_path"],
        }

    if not user_input:
        elapsed = (datetime.now() - state["interrupt_start_time"]).total_seconds()
        if elapsed >= INTERRUPT_TIMEOUT:
            state["interrupted"] = False
            state["next_path"] = "breakpoint_restore"
            logger.info(f"User input timeout ({elapsed:.0f}s), resuming from interrupt")
        await sleep(2)

        return state

    splits = user_input.split(":")
    role = splits[0]
    content = splits[1]
    state["chat_history"] += [HumanMessage(content)]

    result: EndChat | None = await strongly_call(
        input_prompt=CHAT_WITH_USER_PROMPT.format(user_input=user_input),
        action="user_interaction",
        output_schema=EndChat,
    )
    if result is None:
        return state

    if result.end_chat:
        state["interrupted"] = False
        state["next_path"] = "breakpoint_restore"
        logger.info(f"User requested to resume from interrupt")
    elif result.normal_answer:
        state["user_input"] = None
        state["interrupt_start_time"] = datetime.now()
        state["next_path"] = "user_interaction"
        await MesQueue.put(result.normal_answer, role)

    return state


async def breakpoint_restore_node(state: OperatorAgentState) -> OperatorAgentState:
    """restore the agent state from breakpoint"""
    _breakpoint = state["breakpoint_state"]

    state["interrupt_start_time"] = None
    state["user_input"] = None
    state["chat_history"] = []

    if _breakpoint:
        state["current_operation_start_time"] = _breakpoint[
            "current_operation_start_time"
        ]
        state["anomaly_start_time"] = _breakpoint["anomaly_start_time"]
        state["repair_attempt_count"] = _breakpoint["repair_attempt_count"]
        state["short_term_memory"] = _breakpoint["short_term_memory"]
        state["next_path"] = _breakpoint["next_path"]
        logger.info(f"Restored from breakpoint to path: {state['next_path']}")

    return state


async def monitoring_node(state: OperatorAgentState) -> OperatorAgentState:
    """check for anomalies"""
    await sleep(BASE_INTERVAL)

    logger.info("Monitoring for anomalies...")

    try:
        last_record: DetectResultLog = await _get_latest_detection_record()
        if last_record["isAnomaly"]:
            await MesQueue.put("Anomaly detected. Starting root cause analysis...")

            state["anomaly_start_time"] = datetime.fromtimestamp(
                last_record["timestamp"]
            )
            state["current_operation_start_time"] = datetime.now()
            state["repair_attempt_count"] = 0
            state["last_detect_record"] = last_record
            state["next_path"] = "anomaly_analysis"
        else:
            logger.info("No anomaly detected, continuing monitoring...")

    except Exception as e:
        logger.error(f"Monitoring error: {e}")
        state["next_path"] = "monitoring"

    return state


async def anomaly_analysis_node(state: OperatorAgentState) -> OperatorAgentState:
    """analyze the detected anomaly"""
    last_record: DetectResultLog = state["last_detect_record"]

    try:
        # generate anomaly summary of each anomaly type
        anomaly_types = [
            ("systemNetworkAnomaly", last_record["systemNetworkAnomaly"]),
            ("bgpAnomaly", last_record["bgpAnomaly"]),
            ("routerAnomaly", last_record["routerAnomaly"]),
            ("pingAnomaly", last_record["pingAnomaly"]),
            ("topologyAnomaly", last_record["topologyAnomaly"]),
        ]

        summaries = {}
        for anomaly_name, anomaly_data in anomaly_types:
            if anomaly_data:
                logger.info(f"Generating summary for {anomaly_name}...")
                summary_result = await strongly_call(
                    input_prompt=ANOMALY_SUMMARY_PROMPT.format(
                        anomaly_type=anomaly_name, anomaly_data=anomaly_data
                    ),
                    enable_thinking=False,
                )
                if summary_result is None:
                    state["next_path"] = "monitoring"
                    return state

                summaries[anomaly_name] = summary_result.content
                logger.info(
                    f"Summary generated for {anomaly_name}: \n{summary_result.content}"
                )
                await MesQueue.put(
                    f"Summary generated for {anomaly_name}: \n{summary_result.content}"
                )
            else:
                summaries[anomaly_name] = "No anomalies"
                logger.info(f"No data for {anomaly_name}, skipping")

        logger.info("Generating final root cause location analysis...")

        # locate the anomaly location
        final_prompt = ANOMALY_LOCATE_PROMPT.format(
            topology_info=last_record["topologyInfo"],
            system_network_summary=summaries["systemNetworkAnomaly"],
            bgp_summary=summaries["bgpAnomaly"],
            router_summary=summaries["routerAnomaly"],
            ping_summary=summaries["pingAnomaly"],
            topology_summary=summaries["topologyAnomaly"],
        )

        locate_res: AnomalyLocate | None = await strongly_call(
            input_prompt=final_prompt, output_schema=AnomalyLocate
        )
        if locate_res is None:
            state["next_path"] = "monitoring"
            return state

        last_record["directEvidence"] = locate_res.directEvidence or []
        last_record["indirectEvidence"] = locate_res.indirectEvidence or []
        last_record["possibilities"] = [
            p.model_dump() for p in (locate_res.possibilities or [])
        ]
        last_record["attackType"] = locate_res.attackType
        last_record["attackers"] = locate_res.attackers or []
        last_record["victims"] = locate_res.victims or []
        last_record["anomalyStart"] = locate_res.anomalyStart
        last_record["anomalyEnd"] = locate_res.anomalyEnd

        await MesQueue.put(f"Analysis done. Root cause: {locate_res}")
        await _update_latest_detect_record(last_record)

        state["short_term_memory"] = save_short_term_memory(
            state["short_term_memory"],
            {
                "type": "anomaly_analysis",
                "analysis_record": last_record,
            },
        )

        state["next_path"] = "repair_execute"
    except Exception as e:
        logger.error(f"Anomaly analysis error: {e}")
        state["next_path"] = "monitoring"

    return state


async def repair_execute_node(state: OperatorAgentState) -> OperatorAgentState:
    """repairing topology based on anomaly analysis"""
    try:
        state["current_operation_start_time"] = datetime.now()
        # TODO Implement actual repair logic here
        await sleep(WAIT_DURATION)
        state["next_path"] = "force_revoke"
    except Exception as e:
        logger.error(f"Repair execution error: {e}")
        state["next_path"] = "operation_end"

    return state


async def repair_verify_node(state: OperatorAgentState) -> OperatorAgentState:
    """verify if the anomaly is resolved"""
    logger.info("Verifying repair result...")

    start_time = state["current_operation_start_time"]
    elapsed = (datetime.now() - start_time).total_seconds()

    try:
        latest_record: DetectResultLog = await _get_latest_detection_record()

        if not latest_record["isAnomaly"]:
            await MesQueue.put("Network recovered successfully", "operator_agent")
            state["short_term_memory"] = save_short_term_memory(
                state["short_term_memory"],
                {
                    "type": "anomaly_resolved",
                },
            )
            state["next_path"] = "operation_end"
        elif elapsed >= MAX_REPAIR_DURATION:
            logger.info("Repair timeout, try to force revoke...", "operator_agent")
            state["short_term_memory"] = save_short_term_memory(
                state["short_term_memory"],
                {
                    "type": "repair_timeout",
                },
            )
            state["next_path"] = "force_revoke"
        else:
            state["repair_attempt_count"] += 1
            if state["repair_attempt_count"] >= MAX_REPAIR_ATTEMPTS:
                await MesQueue.put(
                    f"Max repair attempts ({MAX_REPAIR_ATTEMPTS}) reached, forcing revoke...",
                    "operator_agent",
                )
                state["next_path"] = "force_revoke"
            else:
                await MesQueue.put(
                    f"Repair not successful, attempt {state['repair_attempt_count']}/{MAX_REPAIR_ATTEMPTS}, retrying...",
                    "operator_agent",
                )
                state["next_path"] = "repair_execute"

    except Exception:
        if elapsed >= MAX_REPAIR_DURATION:
            state["next_path"] = "force_revoke"
        else:
            state["next_path"] = "repair_execute"

    return state


async def force_revoke_node(state: OperatorAgentState) -> OperatorAgentState:
    """force revoke the attack"""
    logger.info("Force revoking attack...")

    try:
        res: ForceRevokeInfo = await _force_revoke()
        if res["success"]:
            await MesQueue.put_extra(
                f"Attack force revoked successfully",
                "operator_agent",
                {
                    "action": "revoke",
                    "attackType": res["attackType"],
                    "attackerName": res["attackerName"],
                    "victimNames": res["victimNames"],
                },
            )
            # ensure the topology have been repaired (so next monitoring won't be abnormal)
            await sleep(4 * BASE_INTERVAL)
        else:
            await MesQueue.put(
                f"Failed to force revoke attack: {res['error']}", "operator_agent"
            )

        state["short_term_memory"] = save_short_term_memory(
            state["short_term_memory"],
            {
                "type": "force_revoked",
            },
        )
    except Exception as e:
        logger.error(f"Force revoke error: {e}")

    state["next_path"] = "operation_end"

    return state


async def operation_end_node(state: OperatorAgentState) -> OperatorAgentState:
    """summarize and save operation data"""
    state["current_operation_start_time"] = None
    state["anomaly_start_time"] = None
    state["repair_attempt_count"] = 0
    state["short_term_memory"] = []
    state["last_detect_record"] = None
    state["next_path"] = "monitoring"

    return state


def route_by_next_path(state: OperatorAgentState) -> str:
    if state["interrupted"]:
        return "user_interaction"
    return state.get("next_path", "monitoring")


def build_workflow() -> StateGraph:
    graph = StateGraph(OperatorAgentState)

    graph.add_node("monitoring", monitoring_node)
    graph.add_node("user_interaction", user_interaction_node)
    graph.add_node("breakpoint_restore", breakpoint_restore_node)
    graph.add_node("anomaly_analysis", anomaly_analysis_node)
    graph.add_node("repair_execute", repair_execute_node)
    graph.add_node("repair_verify", repair_verify_node)
    graph.add_node("force_revoke", force_revoke_node)
    graph.add_node("operation_end", operation_end_node)

    graph.set_entry_point("monitoring")
    graph.add_conditional_edges("monitoring", route_by_next_path)
    graph.add_conditional_edges("user_interaction", route_by_next_path)
    graph.add_conditional_edges("breakpoint_restore", route_by_next_path)
    graph.add_conditional_edges("anomaly_analysis", route_by_next_path)
    graph.add_conditional_edges("repair_execute", route_by_next_path)
    graph.add_conditional_edges("repair_verify", route_by_next_path)
    graph.add_conditional_edges("force_revoke", route_by_next_path)
    graph.add_conditional_edges("operation_end", route_by_next_path)

    return graph


def compile_workflow():
    workflow = build_workflow()
    checkpointer = InMemorySaver()

    return workflow.compile(checkpointer=checkpointer)
