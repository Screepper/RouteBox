from langchain_core.messages import HumanMessage, ToolMessage
from common.agent_logger import logger

from .info_tools import get_latest_detection_record
from .operation_tools import force_revoke
from common.llm import llm, no_reason_llm

tool_dict = {
    "user_interaction": [
        get_latest_detection_record,
    ],
    "anomaly_analysis": [
        get_latest_detection_record,
    ],
    "repair_execute": [
        force_revoke,
    ],
}

tool_call_dict = {
    "user_interaction": {
        "get_latest_detection_record": get_latest_detection_record,
    },
    "anomaly_analysis": {
        "get_latest_detection_record": get_latest_detection_record,
    },
    "repair_execute": {
        "force_revoke": force_revoke,
    },
}


async def strongly_call(
    input_prompt: str, action="", output_schema=None, enable_thinking=True
):
    """call with tools (optional), and return the formatted response (optional)"""
    try:
        used_llm = llm if enable_thinking else no_reason_llm
        messages = [HumanMessage(input_prompt)]

        if action == "":
            if output_schema is None:
                return await used_llm.ainvoke(messages)
            else:
                return await used_llm.with_structured_output(output_schema).ainvoke(
                    messages
                )
        else:
            tool_map = tool_call_dict[action]
            chain = used_llm.bind_tools(tool_dict[action])
            response = await chain.ainvoke(messages)

            tool_messages = []
            if hasattr(response, "tool_calls") and response.tool_calls:
                for tool_call in response.tool_calls:
                    tool_name = tool_call["name"]
                    if tool_name not in tool_map:
                        continue
                    tool = tool_map[tool_name]
                    tool_result = await tool.ainvoke(tool_call["args"])
                    tool_messages.append(
                        ToolMessage(
                            content=(
                                tool_result
                                if isinstance(tool_result, str)
                                else str(tool_result)
                            ),
                            tool_call_id=tool_call["id"],
                        )
                    )

            prompt = messages + [response] + tool_messages
            if output_schema is None:
                return await used_llm.ainvoke(prompt)
            else:
                return await used_llm.with_structured_output(output_schema).ainvoke(
                    prompt
                )
    except Exception as e:
        logger.error(f"strongly_call error: {e}")
        return None
