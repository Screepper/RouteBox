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


class AnomalyLocate(BaseModel):
    """anomaly location analysis result"""

    class ParsedPossibility(BaseModel):
        """parsed anomaly possibility"""

        method: str = Field(description="Attack method used to trigger the anomaly")
        possibility: Literal["High", "Medium", "Low"] = Field(
            description="Possibility of the attack type"
        )
        rationale: str = Field(description="Rationale for this possibility")

    directEvidence: List[str] = Field(
        description="Direct evidence pointing to the anomaly root cause"
    )
    indirectEvidence: List[str] = Field(
        description="Indirect evidence supporting the anomaly judgment"
    )
    possibilities: List[ParsedPossibility] = Field(
        description="Possible attack types with reasoning"
    )
    attackType: str = Field(description="Final identified attack type")
    attackers: List[int] = Field(description="List of attacker node IDs")
    victims: List[int] = Field(description="List of victim node IDs")
    anomalyStart: str = Field(
        description="Anomaly start timestamp (format: yyyy-MM-dd HH:mm:ss)"
    )
    anomalyEnd: str = Field(
        description="Anomaly end timestamp (format: yyyy-MM-dd HH:mm:ss)"
    )
