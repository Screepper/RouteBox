import json
import warnings

warnings.filterwarnings("ignore")

from datetime import datetime, timedelta
from langgraph.graph import StateGraph
from langgraph.checkpoint.memory import InMemorySaver
from langchain_core.messages import HumanMessage
from asyncio import sleep

from .state import AttackerAgentState
from common.agent_logger import logger
from common.remote_call_r import DetectResultLog, AttackableInfo
from common.structure_outputs import (
    EndChat,
    AttackPlanDecision,
    AttackExecuteResult,
    ExperienceFusionResult,
)
from common.prompts import (
    CHAT_WITH_USER_PROMPT,
    ATTACK_PREPARE_PROMPT,
    ATTACK_EXECUTE_PROMPT,
    ANOMALY_SUMMARY_PROMPT,
    EXPERIENCE_FUSION_PROMPT,
)
from tools.attack_tools import _get_attackable_info, check_attackable, recommend_targets
from tools.info_tools import _get_latest_detection_record
from tools.memory_tools import (
    save_short_term_memory,
    _load_experience,
    _save_experience,
)
from tools.tool_util import strongly_call
from config import (
    BASE_INTERVAL,
    INTERRUPT_TIMEOUT,
    COOLING_PERIOD,
    USE_EXPERIENCE,
)
from ws.queue_util import MesQueue


async def user_interaction_node(state: AttackerAgentState) -> AttackerAgentState:
    """the self-running agent was interrupted by user's input, and now it's in user interaction phase"""
    user_input = state["user_input"]

    if state["interrupt_start_time"] is None:
        state["interrupt_start_time"] = datetime.now()
        state["breakpoint_state"] = {
            "expected_next_attack_time": state["expected_next_attack_time"],
            "current_attack_start_time": state["current_attack_start_time"],
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

    state["chat_history"] += [HumanMessage(user_input)]

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
        logger.info("User requested to resume from interrupt")
    elif result.normal_answer:
        state["user_input"] = None
        state["interrupt_start_time"] = datetime.now()
        state["next_path"] = "user_interaction"
        await MesQueue.put(result.normal_answer)

    return state


async def breakpoint_restore_node(state: AttackerAgentState) -> AttackerAgentState:
    """restore the agent state from breakpoint"""
    _breakpoint = state["breakpoint_state"]

    state["interrupt_start_time"] = None
    state["user_input"] = None
    state["chat_history"] = []

    if _breakpoint:
        state["expected_next_attack_time"] = _breakpoint["expected_next_attack_time"]
        state["current_attack_start_time"] = _breakpoint["current_attack_start_time"]
        state["short_term_memory"] = _breakpoint["short_term_memory"]
        state["next_path"] = _breakpoint["next_path"]
        logger.info(f"Restored from breakpoint to path: {state['next_path']}")

    return state


async def cooling_check_node(state: AttackerAgentState) -> AttackerAgentState:
    """check whether it is within the attack launch cooling period"""
    await sleep(BASE_INTERVAL)

    logger.info("Checking cooling time...")

    next_time = state["expected_next_attack_time"]
    try:
        if not next_time:
            latest_record: DetectResultLog = await _get_latest_detection_record()
            if latest_record["isAnomaly"]:
                next_time = datetime.now() + timedelta(seconds=BASE_INTERVAL)
                logger.info("Topology is anomalous, continue waiting...")
            else:
                logger.info("Topology is normal, trigger attack now")
                state["next_path"] = "attack_prepare"
                return state

        if next_time and datetime.now() < next_time:
            logger.info(
                f"Next attack time is {next_time.strftime('%Y-%m-%d %H:%M:%S')}, continue cooling..."
            )
            return state

        logger.info("Cooling complete, ready to attack")
        state["next_path"] = "attack_prepare"
        return state
    except Exception as e:
        logger.error(f"Error checking cooling time: {e}")
        state["next_path"] = "cooling_check"

        return state


async def attack_prepare_node(state: AttackerAgentState) -> AttackerAgentState:
    """prepare attack plan"""
    logger.info("Preparing attack plan...")

    try:
        check_res: bool = await check_attackable()
        if not check_res:
            logger.info(
                "Not attackable, because the stored normal logs are not enough for subsequent detection"
            )
            state["next_path"] = "cooling_check"
            return state

        record: DetectResultLog = await _get_latest_detection_record()
        if record["isAnomaly"]:
            await MesQueue.put("Already triggered anomaly, stop preparing attack plan")
            state["next_path"] = "cooling_check"
            return state

        attackable_info: AttackableInfo = await _get_attackable_info()
        logger.info(
            f"Got attackable info, supporting {len(attackable_info['supportAttackTypes'])} attack types"
        )

        historical_experiences = ""
        if USE_EXPERIENCE:
            historical_experiences = _load_experience()

        prepare_input = ATTACK_PREPARE_PROMPT.format(
            attackable_info=json.dumps(attackable_info, indent=2)
        )
        if historical_experiences:
            prepare_input = f"{prepare_input}\n\n## Historical Attack Experiences (for reference)\n{historical_experiences}"

        decision: AttackPlanDecision | None = await strongly_call(
            input_prompt=prepare_input,
            output_schema=AttackPlanDecision,
        )
        if decision is None:
            return state

        if decision.fetch_recommendation:
            logger.info(f"Fetching recommendations...")
            attack_category_map = {
                "BGP-like": 0,
                "Node-like": 1,
                "Link-like": 2,
            }
            attack_category = attack_category_map.get(decision.belong_to, -1)

            if attack_category == -1:
                logger.error(f"Unknown belong_to type: {decision.belong_to}")
            else:
                attacker_id = (
                    decision.attacker if decision.belong_to == "BGP-like" else None
                )
                result = await recommend_targets(
                    attack_category=attack_category, attacker_id=attacker_id
                )

                if result["success"] and len(result["targets"]) > 0:
                    first_target = result["targets"][0]
                    if (
                        decision.belong_to == "BGP-like"
                        or decision.belong_to == "Node-like"
                    ):
                        recommended_victim = first_target.get("nodeId")
                        if recommended_victim is not None:
                            decision.victims = [recommended_victim]
                            logger.info(
                                f"Updated victims with recommended nodeId: {recommended_victim}"
                            )
                    elif decision.belong_to == "Link-like":
                        recommended_victim = first_target.get("linkId")
                        if recommended_victim is not None:
                            decision.victims = [recommended_victim]
                            logger.info(
                                f"Updated victims with recommended linkId: {recommended_victim}"
                            )
                else:
                    logger.warning(
                        f"Failed to get recommendations or no targets available: {result.get('error', 'unknown error')}"
                    )

        state["short_term_memory"] = save_short_term_memory(
            state["short_term_memory"],
            {
                "type": "attack_plan",
                "attackable_info": attackable_info,
                "plan": decision.model_dump(),
            },
        )

        state["next_path"] = "attack_execute"
        await MesQueue.put(f"Attack Plan: {decision.model_dump()} ")

    except Exception as e:
        logger.error(f"Failed to prepare attack: {e}")
        state["next_path"] = "cooling_check"

    return state


async def attack_execute_node(state: AttackerAgentState) -> AttackerAgentState:
    """execute attack plan"""
    mem = state["short_term_memory"][-1]
    plan = mem["plan"]
    attackable_info: AttackableInfo = mem["attackable_info"]
    attack_type = plan["attack_type"]

    attackerName = ""
    victimNames = []

    if plan["attacker"]:
        for nodeInfo in attackable_info["topologySummary"]["nodes"]:
            if nodeInfo["nodeId"] == plan["attacker"]:
                attackerName = nodeInfo["name"]
                break

    for victimId in plan["victims"]:
        if isinstance(victimId, int):
            for nodeInfo in attackable_info["topologySummary"]["nodes"]:
                if nodeInfo["nodeId"] == victimId:
                    victimNames.append(nodeInfo["name"])
        elif isinstance(victimId, str):
            for linkInfo in attackable_info["topologySummary"]["links"]:
                if linkInfo["linkId"] == victimId:
                    srcNodeId = linkInfo["srcNodeId"]
                    dstNodeId = linkInfo["dstNodeId"]
                    for nodeInfo in attackable_info["topologySummary"]["nodes"]:
                        if nodeInfo["nodeId"] == srcNodeId:
                            victimNames.append(nodeInfo["name"])
                        elif nodeInfo["nodeId"] == dstNodeId:
                            victimNames.append(nodeInfo["name"])

    await MesQueue.put("Attack in progress")
    try:
        result: AttackExecuteResult | None = await strongly_call(
            input_prompt=ATTACK_EXECUTE_PROMPT.format(
                attack_plan=json.dumps(plan, indent=2)
            ),
            action="attack_execute",
            output_schema=AttackExecuteResult,
        )
        if result is None:
            return state

        if result.success:
            state["current_attack_start_time"] = datetime.now()
            state["short_term_memory"] = save_short_term_memory(
                state["short_term_memory"],
                {"type": "attack_executed", "attack_info": result.model_dump()},
            )

            state["next_path"] = "attack_monitor"
            await MesQueue.put_extra(
                f"Attack succeeded. Attack type: {attack_type}",
                extra={
                    "action": "attack",
                    "attackType": attack_type,
                    "attackerName": attackerName,
                    "victimNames": victimNames,
                },
            )
            await sleep(3 * BASE_INTERVAL)
        else:
            await MesQueue.put(
                f"Attack execution failed: {attack_type}, reason: {result.reason}"
            )
            state["next_path"] = "cooling_check"
    except Exception as e:
        logger.error(f"Failed to execute attack: {e}")
        state["next_path"] = "cooling_check"

    return state


async def attack_monitor_node(state: AttackerAgentState) -> AttackerAgentState:
    # ensure the attack has been executed (i.e., if the attack is effective, the topology is now abnormal, or the attack is failed)
    await sleep(BASE_INTERVAL)
    logger.info("Monitoring attack status...")

    try:
        record: DetectResultLog = await _get_latest_detection_record()

        if not record["isAnomaly"]:
            state["next_path"] = "attack_end"
            logger.info("Anomaly already resolved by OperatorAgent")
            await sleep(COOLING_PERIOD)
        else:
            # record anomaly details the first time when anomalies are detected
            if state["short_term_memory"][-1]["type"] == "attack_executed":
                state["short_term_memory"] = save_short_term_memory(
                    state["short_term_memory"],
                    {
                        "type": "anomaly_detected",
                        "anomaly_record": record,
                    },
                )
                # continue monitoring the attack
            logger.info("Anomaly exists, continue monitoring...")

    except Exception as e:
        logger.error(f"Monitor check failed: {e}")
        state["next_path"] = "attack_monitor"

    return state


async def attack_end_node(state: AttackerAgentState) -> AttackerAgentState:
    """end and summarize this attack, then fuse with historical experiences"""
    logger.info("Generating anomaly abstract and fusing experiences...")

    short_term_memory = state["short_term_memory"]
    if (
        len(short_term_memory) < 1
        or short_term_memory[-1]["type"] != "anomaly_detected"
    ):
        logger.error("No anomaly detected, skip abstract generation")
        return state

    try:
        anomaly_record = short_term_memory[-1]["anomaly_record"]
        # generate anomaly summary of each anomaly type
        anomaly_types = [
            ("systemNetworkAnomaly", anomaly_record["systemNetworkAnomaly"]),
            ("bgpAnomaly", anomaly_record["bgpAnomaly"]),
            ("routerAnomaly", anomaly_record["routerAnomaly"]),
            ("pingAnomaly", anomaly_record["pingAnomaly"]),
            ("topologyAnomaly", anomaly_record["topologyAnomaly"]),
        ]
        for anomaly_name, anomaly_data in anomaly_types:
            if anomaly_data:
                logger.info(f"Generating summary for {anomaly_name}...")
                summary_result = await strongly_call(
                    input_prompt=ANOMALY_SUMMARY_PROMPT.format(
                        anomaly_type=anomaly_name, anomaly_data=anomaly_data
                    )
                )
                if summary_result is None:
                    return state

                # update anomaly record with summary
                short_term_memory[-1]["anomaly_record"][anomaly_name] = summary_result
                logger.info(
                    f"Summary generated for {anomaly_name}: \n{summary_result.content}"
                )
            else:
                short_term_memory[-1]["anomaly_record"][anomaly_name] = "No anomalies"
                logger.info(f"No data for {anomaly_name}, skipping")

        historical_experiences = _load_experience()
        logger.info("Fusing with historical experiences...")
        fusion_result: ExperienceFusionResult | None = await strongly_call(
            input_prompt=EXPERIENCE_FUSION_PROMPT.format(
                attack_summary=json.dumps(short_term_memory, indent=2, default=str),
                historical_experiences=(
                    historical_experiences
                    if historical_experiences
                    else "No historical experiences yet. This is the first attack experience."
                ),
            ),
            output_schema=ExperienceFusionResult,
        )
        if fusion_result is None:
            return state

        _save_experience(fusion_result.strategy_library)
        logger.info("Experience fused and saved successfully")

        logger.info(f"New patterns identified: {fusion_result.new_patterns_identified}")
        logger.info(f"Improvements applied: {fusion_result.improvements_applied}")

    except Exception as e:
        logger.error(f"Failed to update experiences: {e}")

    state["expected_next_attack_time"] = None
    state["short_term_memory"] = []
    state["next_path"] = "cooling_check"

    return state


def route_by_next_path(state: AttackerAgentState) -> str:
    if state["interrupted"]:
        return "user_interaction"
    return state.get("next_path", "cooling_check")


def build_workflow() -> StateGraph:
    graph = StateGraph(AttackerAgentState)

    graph.add_node("cooling_check", cooling_check_node)
    graph.add_node("user_interaction", user_interaction_node)
    graph.add_node("breakpoint_restore", breakpoint_restore_node)
    graph.add_node("attack_prepare", attack_prepare_node)
    graph.add_node("attack_execute", attack_execute_node)
    graph.add_node("attack_monitor", attack_monitor_node)
    graph.add_node("attack_end", attack_end_node)

    graph.set_entry_point("cooling_check")
    graph.add_conditional_edges("cooling_check", route_by_next_path)
    graph.add_conditional_edges("user_interaction", route_by_next_path)
    graph.add_conditional_edges("breakpoint_restore", route_by_next_path)
    graph.add_conditional_edges("attack_prepare", route_by_next_path)
    graph.add_conditional_edges("attack_execute", route_by_next_path)
    graph.add_conditional_edges("attack_monitor", route_by_next_path)
    graph.add_conditional_edges("attack_end", route_by_next_path)

    return graph


def compile_workflow():
    workflow = build_workflow()
    checkpointer = InMemorySaver()

    return workflow.compile(checkpointer=checkpointer)
