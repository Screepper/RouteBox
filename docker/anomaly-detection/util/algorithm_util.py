from abc import ABC
from typing import List, Tuple
from config import COMBINED_SECONDS

NOISE_THRESHOLD = (
    2 * COMBINED_SECONDS
)  # The max count of continuous time steps that can be considered as noise (NOISE_THRESHOLD+1 is the min allowable anomaly)
CONTINUOUS_ANOMALY_THRESHOLD = (
    1 * COMBINED_SECONDS
)  # The max gap of time interval that can be considered as a completely continuous anomaly
SAME_ANOMALY_THRESHOLD = (
    2 * COMBINED_SECONDS
)  # The max count of anomaly time ranges that can be considered as a same anomaly
MAX_PREV_ANOMALY_GAP = (
    4 * COMBINED_SECONDS
)  # The max gap of time interval that can be considered as a previous anomaly


class AlgorithmUtil(ABC):

    @staticmethod
    def process_time_ranges(anomalous_times: List[int]) -> List[Tuple[int, int]]:
        if not anomalous_times:
            return []

        merged_time_ranges = AlgorithmUtil.merge_anomaly_time_steps(anomalous_times)
        merge_merged_time_ranges = AlgorithmUtil.merge_anomaly_time_ranges(
            merged_time_ranges
        )
        filtered_time_ranges = AlgorithmUtil.filter_noise_time_ranges(
            merge_merged_time_ranges
        )

        return filtered_time_ranges

    @staticmethod
    def merge_anomaly_time_steps(anomalous_times: List[int]) -> List[Tuple[int, int]]:
        """Merge anomaly time steps that have small gap"""
        time_ranges = []
        current_start = anomalous_times[0]
        current_end = current_start

        for time in anomalous_times[1:]:
            gap = time - current_end
            if gap > CONTINUOUS_ANOMALY_THRESHOLD:
                time_ranges.append((current_start, current_end))
                current_start = time
            current_end = time
        time_ranges.append((current_start, current_end))

        return time_ranges

    @staticmethod
    def merge_anomaly_time_ranges(
        time_ranges: List[Tuple[int, int]],
    ) -> List[Tuple[int, int]]:
        """Merge anomaly time ranges that have small gap"""
        if not time_ranges:
            return []

        merged = []
        current = time_ranges[0]

        for next_range in time_ranges[1:]:
            if next_range[0] - current[1] <= SAME_ANOMALY_THRESHOLD:
                current = (current[0], max(current[1], next_range[1]))
            else:
                merged.append(current)
                current = next_range
        merged.append(current)

        return merged

    @staticmethod
    def filter_noise_time_ranges(
        time_ranges: List[Tuple[int, int]],
    ) -> List[Tuple[int, int]]:
        """Filter anomaly time ranges last for short time"""
        if not time_ranges:
            return []

        is_small = []
        for start, end in time_ranges:
            is_small.append(end - start + 1 <= NOISE_THRESHOLD)
        keep_small_indices = set()

        # If the small range is close to the previous one, then remain it
        for i, small in enumerate(is_small):
            if small:
                if (
                    i > 0
                    and is_small[i - 1]
                    and time_ranges[i][0] - time_ranges[i - 1][1]
                    <= SAME_ANOMALY_THRESHOLD
                ):
                    keep_small_indices.add(i - 1)
                    keep_small_indices.add(i)
                if (
                    i < len(time_ranges) - 1
                    and is_small[i + 1]
                    and time_ranges[i + 1][0] - time_ranges[i][1]
                    <= SAME_ANOMALY_THRESHOLD
                ):
                    keep_small_indices.add(i + 1)
                    keep_small_indices.add(i)

        return [
            time_ranges[i]
            for i in range(len(time_ranges))
            if not is_small[i] or i in keep_small_indices
        ]
