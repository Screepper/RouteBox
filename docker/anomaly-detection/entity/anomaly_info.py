from pydantic import BaseModel


class AnomalyInfo(BaseModel):
    nodeId: int = 0
    startTime: int = 0
    endTime: int = 0
    fieldName: str = ""
    description: str = ""

    def __init__(
        self,
        node_id: int,
        start_time: int,
        end_time: int,
        field_name: str,
        description: str,
    ):
        super().__init__(
            nodeId=node_id,
            startTime=start_time,
            endTime=end_time,
            fieldName=field_name,
            description=description,
        )
