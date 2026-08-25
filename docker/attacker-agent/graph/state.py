from typing import TypedDict, Optional, List, Dict
from datetime import datetime
from langgraph.graph.message import BaseMessage


class AttackerAgentState(TypedDict):
    """attacker agent state"""
    # attack related
    expected_next_attack_time: Optional[datetime]
    current_attack_start_time: Optional[datetime]
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
