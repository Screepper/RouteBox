from pydantic import BaseModel, Field
from typing import List, Literal


class EndChat(BaseModel):
    """user chat"""

    normal_answer: str = Field(
        description="The normal answer to the user's question, if the user wants to continue conversation, this field should not be empty",
    )
    end_chat: bool = Field(
        description="Decide whether to exit the chat status according to the user's input"
    )


class AttackPlanDecision(BaseModel):
    """attack plan decision"""

    attack_type: str = Field(
        description="The selected attack type from the available attack types"
    )
    belong_to: Literal["BGP-like", "Node-like", "Link-like"] = Field(
        description="The attack type belongs to which category"
    )
    serial_number: int = Field(description="The serial number of the attack type")
    attacker: int | None = Field(
        description="attackerNodeId if the attack type requires a specific attacker, otherwise None"
    )
    victims: List[int | str] = Field(
        description="List of target ids (nodeId (int) or linkId (str), but not both)"
    )
    rationale: str = Field(
        description="Explanation of why this attack type and parameters were chosen"
    )
    fetch_recommendation: bool = Field(
        description="Whether to fetch recommended attack targets from the backend before executing the attack"
    )


class AttackExecuteResult(BaseModel):
    """attack execute result"""

    success: bool = Field(description="Whether the attack execution is successful")
    attack_id: str = Field(description="Attack ID if the execution is successful")
    attack_type: str = Field(description="Attack type if the execution is successful")
    reason: str = Field(
        description="Reason for the execution result (adopt only when the execution is unsuccessful)"
    )


class ExperienceFusionResult(BaseModel):
    """experience fusion result"""

    strategy_library: str = Field(
        description="The updated strategy library in markdown format"
    )
    new_patterns_identified: List[str] = Field(
        description="New patterns identified from this experience"
    )
    improvements_applied: List[str] = Field(
        description="Improvements applied to existing strategies"
    )
