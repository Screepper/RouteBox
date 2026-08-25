from abc import ABC, abstractmethod
from typing import List

from entity.anomaly_info import AnomalyInfo
from entity.system_network_log import SystemNetworkLog


class BaseDetector(ABC):
    @abstractmethod
    def process(
        self,
        current_logs: List[SystemNetworkLog],
        recent_logs: List[SystemNetworkLog],
        period_logs: List[SystemNetworkLog],
    ) -> List[AnomalyInfo]:
        """
        Detect anomalies in the systemNetwork Logs

        Args:
            current_logs: The current systemNetwork Logs
            recent_logs: The recent systemNetwork Logs
            period_logs: The period systemNetwork Logs

        Returns:
            The list of anomalies in AnomalyInfo structure
        """

        pass
