from typing import TypedDict, Optional, List, Dict, Any
from datetime import datetime
from langgraph.graph.message import BaseMessage


class OperatorAgentState(TypedDict):
    """operator agent state"""

    # operation related
    current_operation_start_time: Optional[datetime]
    anomaly_start_time: Optional[datetime]
    repair_attempt_count: int
    # memory related
    short_term_memory: List[dict]
    # user interaction related
    interrupted: bool
    interrupt_start_time: Optional[datetime]
    breakpoint_state: Optional[Dict]
    user_input: Optional[str]
    chat_history: List[BaseMessage]
    # graph flow related
    next_path: str
    # last detect record related
    last_detect_record: Optional[Dict[str, Any]]
