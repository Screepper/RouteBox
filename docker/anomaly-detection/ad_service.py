from concurrent.futures import ProcessPoolExecutor, as_completed
from time import time
from typing import List, Dict

from algorithm.base import BaseDetector
from entity.anomaly_info import AnomalyInfo
from entity.system_network_log import SystemNetworkLog


def process_node(algorithm, args: Dict):
    return algorithm.process(**args)


class ADService:
    def __init__(self, detector: BaseDetector):
        self.detector = detector

    def detect(
        self,
        current_dict: Dict[int, List[SystemNetworkLog]],
        recent_dict: Dict[int, List[SystemNetworkLog]],
        period_dict: Dict[int, List[SystemNetworkLog]],
    ) -> List[AnomalyInfo]:
        start_time = time()
        results = []

        with ProcessPoolExecutor(max_workers=10) as executor:
            future_to_node = {}

            for nodeId, logs in current_dict.items():
                more_args = {
                    "current_logs": logs,
                    "recent_logs": recent_dict[nodeId],
                    "period_logs": period_dict[nodeId],
                }
                future = executor.submit(process_node, self.detector, more_args)
                future_to_node[future] = nodeId

            complete_count = 0
            for future in as_completed(future_to_node):
                nodeId = future_to_node[future]
                anomaly_infos = future.result()
                complete_count += 1
                print(
                    f"Node{nodeId} detected {len(anomaly_infos)} anomalies, Progress: [{complete_count}/{len(future_to_node)}]"
                )
                results.extend(anomaly_infos)
        elapsed = time() - start_time
        print(f"Detection completed in {elapsed:.2f} s")

        return results
