import os
from typing import List, Tuple, Dict

import numpy as np

from dataset_parse_util import get_field_names, extract_field_values
from dataset_parse_util import row_data_to_log

PERIOD_SECONDS = 60 * 60 * 24 * 7  # period of 7 days
WINDOW_SIZE = 60
SLIDE_SIZE = 10
LAGS = 30
BASE_MIN_EXPANSION = 1.25
COMBINED_SECONDS = 30
NOISE_THRESHOLD = (
        4 * COMBINED_SECONDS
)  # The max count of continuous time steps that can be considered as noise (NOISE_THRESHOLD+1 is the min allowable anomaly)
CONTINUOUS_ANOMALY_THRESHOLD = (
        1 * COMBINED_SECONDS
)  # The max gap of time interval that can be considered as a completely continuous anomaly
SAME_ANOMALY_THRESHOLD = (
        2 * COMBINED_SECONDS
)  # The max count of anomaly time ranges that can be considered as a same anomaly


def load_csv_logs(path: str) -> list:
    """Parse CSV file for a single node into SystemNetworkLog list"""
    if not os.path.exists(path):
        raise FileNotFoundError(f"File not found: {path}")
    with open(path, "r", encoding="utf-8") as f:
        return [row_data_to_log(line.strip()) for line in f]


def find_train_window_index(train_logs: list, target_ts: int, _i: int) -> int:
    """Find the index of the closest timestamp in train_logs to target_ts """
    train_start_time = train_logs[0].timestamp

    while target_ts - PERIOD_SECONDS > train_start_time:
        target_ts -= PERIOD_SECONDS

    best_idx = 0
    min_diff = float("inf")
    for i, log in enumerate(train_logs):
        diff = abs(log.timestamp - target_ts)
        if diff < min_diff:
            min_diff = diff
            best_idx = i
        elif diff > min_diff and i > 0:
            break

    return best_idx


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


def process_time_ranges(anomalous_times: List[int]) -> List[Tuple[int, int]]:
    if not anomalous_times:
        return []

    merged_time_ranges = merge_anomaly_time_steps(anomalous_times)
    merge_merged_time_ranges = merge_anomaly_time_ranges(merged_time_ranges)
    filtered_time_ranges = filter_noise_time_ranges(merge_merged_time_ranges)

    return filtered_time_ranges


class METED:
    def eval(
            self,
            train_path: str,
            eval_path: str,
    ) -> Tuple[float, float, float]:
        train_logs = load_csv_logs(train_path)
        eval_logs = load_csv_logs(eval_path)
        if not train_logs or not eval_logs:
            raise ValueError(f"Data missing in file {train_path} or {eval_path}")

        prec_sum, rec_sum, f1_sum = 0.0, 0.0, 0.0
        window_cnt = 0

        for i in range(0, len(eval_logs), WINDOW_SIZE):
            current_logs = eval_logs[i: i + WINDOW_SIZE]
            # only 10 points here (can guarantee that these points are normal), but 120 for real-time detection.
            recent_logs = current_logs[:10]

            start_ts = current_logs[0].timestamp
            idx = find_train_window_index(train_logs, start_ts, i)

            period_logs = train_logs[idx: idx + 60]  # period 60 points
            p, r, f = self.eval_win(current_logs, recent_logs, period_logs)
            prec_sum += p
            rec_sum += r
            f1_sum += f
            window_cnt += 1

        return prec_sum / window_cnt, rec_sum / window_cnt, f1_sum / window_cnt

    def eval_win(self, current_logs, recent_logs, period_logs) -> Tuple[float, float, float]:
        current_times = [log.timestamp for log in current_logs]
        time_labels = {timestamp: 0 for timestamp in current_times}
        field_names = get_field_names(current_logs[0])

        current_values = {
            field: extract_field_values(current_logs, field) for field in field_names
        }
        recent_values = {
            field: extract_field_values(recent_logs, field) for field in field_names
        }
        period_values = {
            field: extract_field_values(period_logs, field) for field in field_names
        }
        field_anomalies = {}

        for field in field_names:
            if field in ["tps", "ioWriteKb"]:  # skip these fields (no-related anomaly)
                continue
            recent_median = self._extract_median(recent_values[field])
            period_median = self._extract_median(period_values[field])
            pred_median = self._rolling_pred(recent_median)
            upper_exp, lower_exp = self._calculate_expansion(field, recent_values[field], recent_median)
            pred_median_corr = self._correct_median(pred_median, period_median)

            pred_upper = pred_median_corr + upper_exp
            pred_lower = pred_median_corr - lower_exp

            anomalies = self._judge_anomaly(current_values[field], pred_upper, pred_lower, current_times)
            field_anomalies[field] = anomalies

        self._generate_anomaly_labels(time_labels, field_anomalies)

        return self._compute_metrics(current_logs, time_labels)

    def _extract_median(self, values: List[float]) -> np.ndarray:
        n = len(values)
        window_medians = [
            np.median(values[i: i + SLIDE_SIZE]) for i in range(0, n, SLIDE_SIZE)
        ]
        x_orig = np.arange(len(window_medians)) * SLIDE_SIZE + SLIDE_SIZE // 2
        return np.interp(np.arange(n), x_orig, window_medians)

    def _rolling_pred(self, median_line: np.ndarray) -> np.ndarray:
        pred_results = []
        recent_rolls = median_line[-LAGS:].copy()
        for _ in range(WINDOW_SIZE):
            pred = np.mean(recent_rolls)
            pred_results.append(pred)
            recent_rolls = np.roll(recent_rolls, -1)
            recent_rolls[-1] = pred
        return np.array(pred_results) - (pred_results[0] - median_line[-1])

    def _calculate_expansion(self, field: str, recent_data: List[float], median_line: np.ndarray) -> Tuple[
        float, float]:
        std_dev, mean_val = float(np.std(recent_data)), float(np.mean(recent_data))
        if mean_val == 0.0:
            return 0.0, 0.0

        exp_factor = 2.5 if "Rate" in field else 2.0
        exp_factor = float(np.clip(exp_factor + 10 * (std_dev / mean_val), 2.0, 5.0))

        recent_np = np.array(recent_data)
        up_mask, low_mask = recent_np >= median_line, recent_np <= median_line
        avg_up = (
            np.mean(recent_np[up_mask] - median_line[up_mask])
            if np.any(up_mask)
            else 0.0
        )
        avg_low = (
            np.mean(median_line[low_mask] - recent_np[low_mask])
            if np.any(low_mask)
            else 0.0
        )

        up_exp, low_exp = avg_up * exp_factor, avg_low * exp_factor
        q15, q85 = float(np.percentile(recent_data, 15)), float(
            np.percentile(recent_data, 85)
        )
        min_up = max(0.0, (q85 - float(median_line[-1])) * BASE_MIN_EXPANSION)
        min_low = max(0.0, (float(median_line[-1]) - q15) * BASE_MIN_EXPANSION)
        return max(up_exp, min_up), max(low_exp, min_low)

    def _correct_median(self, pred_median: np.ndarray, period_median: np.ndarray) -> np.ndarray:
        corrected = pred_median.copy()
        trends = []
        for i in range(1, len(period_median)):
            prev = period_median[i - 1]
            trends.append((period_median[i] - prev) / prev if prev != 0 else 0.0)

        ratio = 0.9
        for i in range(1, len(corrected)):
            prev_c = corrected[i - 1]
            corrected[i] = (
                    prev_c
                    + prev_c * trends[i - 1] * ratio
                    + (corrected[i] - prev_c) * (1 - ratio)
            )
        return corrected

    def _judge_anomaly(
            self,
            actual: List[float],
            pred_upper: np.ndarray,
            pred_lower: np.ndarray,
            timestamps: List[int],
    ) -> Dict[str, List[int]]:
        anomalies = {"upper": [], "lower": []}
        for i in range(min(len(actual), WINDOW_SIZE)):
            if actual[i] > pred_upper[i]:
                anomalies["upper"].append(timestamps[i])
            elif actual[i] < pred_lower[i]:
                anomalies["lower"].append(timestamps[i])
        return anomalies

    def _generate_anomaly_labels(
            self, time_labels: Dict[int, int], field_anomalies: Dict[str, Dict[str, List[int]]]
    ):
        for anomaly_dict in field_anomalies.values():
            for ts_list in anomaly_dict.values():
                if not ts_list:
                    continue
                for start, end in process_time_ranges(ts_list):
                    cur = start
                    while cur <= end:
                        time_labels[cur] = 1
                        cur += COMBINED_SECONDS

    def _compute_metrics(self, current_logs: List, time_labels: Dict[int, int]) -> Tuple[float, float, float]:
        y_true = [int(log.label) for log in current_logs]
        y_pred = [time_labels.get(log.timestamp, 0) for log in current_logs]

        tp = sum(1 for t, p in zip(y_true, y_pred) if t == 1 and p == 1)
        fp = sum(1 for t, p in zip(y_true, y_pred) if t == 0 and p == 1)
        fn = sum(1 for t, p in zip(y_true, y_pred) if t == 1 and p == 0)

        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = (
            2 * precision * recall / (precision + recall)
            if (precision + recall) > 0
            else 0.0
        )

        return precision, recall, f1
