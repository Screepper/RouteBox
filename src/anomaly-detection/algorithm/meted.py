import os
import time
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from typing import List, Dict, Tuple, Optional
from config import DETECT_WINDOW_SIZE, REAL_TIME_MODE
from util.algorithm_util import MAX_PREV_ANOMALY_GAP
from entity.anomaly_info import AnomalyInfo
from entity.system_network_log import SystemNetworkLog
from util.algorithm_util import AlgorithmUtil
from util.dataset_parse_util import DatasetParseUtil
from algorithm.base import BaseDetector

SLIDE_SIZE = 10  # Slide size for sum-based anomaly detection
LAGS = 30  # Lag size for Linear Regression
BASE_MIN_EXPANSION = 1.25  # Base min expansion factor


class METED(BaseDetector):
    def __init__(self, plot_save: bool = False, plot_save_dir: str = "./images/detect"):
        self.plot_save = plot_save
        self.plot_save_dir = plot_save_dir
        if plot_save:
            os.makedirs(self.plot_save_dir, exist_ok=True)

    @staticmethod
    def get_period_thresholds(field: str) -> Tuple[float, float]:
        """Get sum-based anomaly detection thresholds"""
        if field in ["memoryUseRatio", "usedMemoryKb"]:
            return 2.5, 0.4
        elif field in ["tps", "ioWriteKb"]:
            return 2.25, 0.44
        elif field in ["cpuUseRatio", "systemUseRatio"]:
            return 1.35, 0.74
        else:
            return 1.05, 0.95

    def _detect_sum(
        self, field: str, current_sum: float, period_sum: Optional[float]
    ) -> bool:
        """Fast detect sum anomaly"""
        if field in ["tps", "ioWriteKb"]:
            return False

        upper, lower = self.get_period_thresholds(field)

        return (current_sum > upper * period_sum) or (current_sum < lower * period_sum)

    def process(
        self,
        current_logs: List[SystemNetworkLog],
        recent_logs: List[SystemNetworkLog],
        period_logs: List[SystemNetworkLog],
    ) -> List[AnomalyInfo]:
        nodeId = current_logs[0].nodeId
        current_times = [log.timestamp for log in current_logs]

        # ===== Step 1: Sum-based anomaly filtering =====
        field_names = DatasetParseUtil.get_field_names(current_logs[0])

        # Calculate sum values for current and period windows
        current_data = {
            field: DatasetParseUtil.extract_field_values(current_logs, field)
            for field in field_names
        }
        current_sums = {field: sum(vals) for field, vals in current_data.items()}
        period_data = {
            field: DatasetParseUtil.extract_field_values(period_logs, field)
            for field in field_names
        }
        period_sums = {field: sum(vals) for field, vals in period_data.items()}

        # Filter fields with sum-based anomalies
        sum_anomaly_map = {
            field: self._detect_sum(
                field=field,
                current_sum=current_sums[field],
                period_sum=period_sums[field],
            )
            for field in field_names
        }
        sum_anomalous_fields = [
            field for field, anomalous in sum_anomaly_map.items() if anomalous
        ]

        # Early return if no sum-based anomalies
        if not sum_anomalous_fields:
            return []

        # ===== Step 2: Feature-based detailed detection with median line approach =====
        recent_values = {
            field: DatasetParseUtil.extract_field_values(recent_logs, field)
            for field in sum_anomalous_fields
        }
        current_values = {field: current_data[field] for field in sum_anomalous_fields}
        period_values = {field: period_data[field] for field in sum_anomalous_fields}
        field_anomalies = {}

        for field in sum_anomalous_fields:
            # Extract median line for recent data and period data
            recent_median = self._extract_median(recent_values[field])
            period_median = self._extract_median(period_values[field])

            # Predict median line for current data
            pred_median = self._rolling_pred(recent_median)

            # Calculate expansion range
            upper_expansion, lower_expansion = self._calculate_expansion(
                field, recent_values[field], recent_median
            )

            # Apply period trend correction to pred median
            pred_median_corrected = self._correct_median(pred_median, period_median)

            # Create upper and lower bounds from corrected median
            pred_upper = pred_median_corrected + upper_expansion
            pred_lower = pred_median_corrected - lower_expansion

            # Detection
            anomalies = self._judge_anomaly(
                actual=current_values[field],
                pred_upper=pred_upper,
                pred_lower=pred_lower,
                timestamps=current_times,
            )
            field_anomalies[field] = anomalies

            # Save plot
            if self.plot_save:
                self._plot_result(
                    node_id=nodeId,
                    field=field,
                    recent_ts=[log.timestamp for log in recent_logs],
                    recent_values=recent_values[field],
                    current_values=current_values[field],
                    recent_median=recent_median,
                    pred_median=pred_median_corrected,
                    period_median=period_median,
                    pred_upper=pred_upper,
                    pred_lower=pred_lower,
                    current_ts=current_times,
                    anomalies=anomalies,
                )

        anomaly_info = self._generate_anomaly_info(
            node_id=nodeId,
            recent_values=recent_values,
            current_values=current_values,
            current_win_times=current_times,
            field_anomalies=field_anomalies,
        )

        if REAL_TIME_MODE:
            return self._filter_stale_anomalies(nodeId, anomaly_info)

        return anomaly_info

    @staticmethod
    def _filter_stale_anomalies(
        nodeId: str,
        anomaly_list: List[AnomalyInfo],
    ) -> List[AnomalyInfo]:
        if not anomaly_list:
            return []

        current_time = int(time.time())
        latest_anomaly_time = max(a.endTime for a in anomaly_list)

        anomaly_time_interval = current_time - latest_anomaly_time

        if anomaly_time_interval > MAX_PREV_ANOMALY_GAP:
            print(
                f"Node {nodeId} has no recent anomaly for {anomaly_time_interval} s, over the threshold {MAX_PREV_ANOMALY_GAP} s"
            )
            return []

        return anomaly_list

    @staticmethod
    def _extract_median(values: List[float]) -> np.ndarray:
        """Extract median line from values using sliding window median"""
        n = len(values)
        window_medians = []

        for i in range(0, n, SLIDE_SIZE):
            window = values[i : i + SLIDE_SIZE]
            window_median = np.median(window)
            window_medians.append(window_median)

        # Interpolate
        x_original = np.arange(len(window_medians)) * SLIDE_SIZE + SLIDE_SIZE // 2
        x_target = np.arange(n)
        median_line = np.interp(x_target, x_original, window_medians)

        return median_line

    @staticmethod
    def _rolling_pred(median_line: np.ndarray) -> np.ndarray:
        pred_results = []
        recent_rolls = median_line[-LAGS:].copy()

        # Simple moving average
        for _ in range(DETECT_WINDOW_SIZE):
            pred = np.mean(recent_rolls)
            pred_results.append(pred)

            # Shift left and add new
            recent_rolls = np.roll(recent_rolls, -1)
            recent_rolls[-1] = pred

        # Shift the predicted to the recent
        delta = pred_results[0] - median_line[-1]
        pred_results -= delta

        return np.array(pred_results)

    @staticmethod
    def _calculate_expansion(
        field: str, recent_data: List[float], median_line: np.ndarray
    ) -> Tuple[float, float]:
        # Calculate volatility
        std_dev = float(np.std(recent_data))
        mean_val = float(np.mean(recent_data))

        # If mean is 0 (e.g., dropped count fields normally at 0), return 0 expansion
        if mean_val == 0.0:
            return 0.0, 0.0

        # Calculate expansion factor
        expansion_factor = 2.5 if "Rate" in field else 1.5
        expansion_factor = (
            expansion_factor + 10 * (std_dev / mean_val) if mean_val != 0 else 3.0
        )
        expansion_factor = np.clip(expansion_factor, 2.0, 5.0)

        recent_np = np.array(recent_data.copy())

        # Upper expansion: average distance of data points above the median line
        upper_mask = recent_np >= median_line
        upper_distances = recent_np[upper_mask] - median_line[upper_mask]
        avg_upper_distance = np.mean(upper_distances)

        # Lower expansion: average distance of data points below the median line
        lower_mask = recent_np <= median_line
        lower_distances = median_line[lower_mask] - recent_np[lower_mask]
        avg_lower_distance = np.mean(lower_distances)

        # Calculate expansion
        upper_expansion = avg_upper_distance * expansion_factor
        lower_expansion = avg_lower_distance * expansion_factor

        # Calculate percentiles
        q15 = float(np.percentile(recent_data, 15))
        q85 = float(np.percentile(recent_data, 85))

        # Ensure min expansion
        min_upper_expansion = max(
            0.0, (q85 - float(median_line[-1])) * BASE_MIN_EXPANSION
        )
        min_lower_expansion = max(
            0.0, (float(median_line[-1]) - q15) * BASE_MIN_EXPANSION
        )

        # Ensure expanded upper are not less than q85
        upper_expansion = max(upper_expansion, min_upper_expansion)

        # Ensure expanded lower are not greater than q15
        lower_expansion = max(lower_expansion, min_lower_expansion)

        return upper_expansion, lower_expansion

    @staticmethod
    def _correct_median(
        pred_median: np.ndarray, period_median: np.ndarray
    ) -> np.ndarray:
        corrected_median = pred_median.copy()

        # Calculate period trends (relative change series)
        period_trends = []
        for i in range(1, len(period_median)):
            previous_val = period_median[i - 1]
            current_val = period_median[i]
            if previous_val == 0:
                trend = 0.0
            else:
                trend = (current_val - previous_val) / previous_val
            period_trends.append(trend)

        # Apply trends to each point
        period_ratio = 0.6
        for i in range(1, len(corrected_median)):
            previous_corrected = corrected_median[i - 1]
            corrected_median[i] = (
                previous_corrected
                + previous_corrected * period_trends[i - 1] * period_ratio
                + (corrected_median[i] - previous_corrected) * (1 - period_ratio)
            )

        return corrected_median

    @staticmethod
    def _judge_anomaly(
        actual: List[float],
        pred_upper: np.ndarray,
        pred_lower: np.ndarray,
        timestamps: List[int],
    ) -> Dict[str, List[int]]:
        anomalies = {"upper": [], "lower": []}

        for i in range(DETECT_WINDOW_SIZE):
            ts = timestamps[i]
            if actual[i] > pred_upper[i]:
                anomalies["upper"].append(ts)
            elif actual[i] < pred_lower[i]:
                anomalies["lower"].append(ts)

        return anomalies

    def _plot_result(
        self,
        node_id: int,
        field: str,
        recent_ts: List[int],
        recent_values: List[float],
        current_values: List[float],
        recent_median: np.ndarray,
        pred_median: np.ndarray,
        period_median: np.ndarray,
        pred_upper: np.ndarray,
        pred_lower: np.ndarray,
        current_ts: List[int],
        anomalies: Dict[str, List[int]],
    ):
        """Plot detection results with median line"""
        # Combine recent and current timestamps
        all_ts = recent_ts + current_ts
        all_len = len(all_ts)
        combined_data = np.concatenate([recent_values, current_values])

        # Convert timestamps to readable datetime
        plot_times = pd.to_datetime(all_ts, unit="s", utc=True).tz_convert(
            "Asia/Shanghai"
        )
        time_labels = plot_times.strftime("%H:%M:%S")

        # X-axis indices
        recent_len = len(recent_ts)
        recent_x = range(recent_len)
        current_x = range(recent_len, all_len)
        total_x = range(all_len)

        # Create plot
        _, ax = plt.subplots(figsize=(12, 5))
        ax.plot(
            recent_x,
            recent_median,
            "g-",
            alpha=0.8,
            linewidth=1.2,
            label="Recent Median",
        )
        ax.plot(
            current_x,
            pred_median,
            "g--",
            alpha=0.8,
            linewidth=1.2,
            label="Predicted Median",
        )
        ax.plot(
            total_x,
            combined_data,
            "b-",
            alpha=0.8,
            linewidth=1.0,
            label="Actual Values",
        )
        ax.plot(
            current_x,
            period_median,
            "k--",
            alpha=0.8,
            linewidth=1.0,
            label="Period Median",
        )
        ax.fill_between(current_x, pred_lower, pred_upper, color="orange", alpha=0.2)
        anomaly_x, anomaly_y = [], []

        for ts in anomalies["upper"] + anomalies["lower"]:
            if ts in current_ts:
                current_idx = current_ts.index(ts)
                anomaly_idx = len(recent_ts) + current_idx
                anomaly_x.append(anomaly_idx)
                anomaly_y.append(current_values[current_idx])

        if anomaly_x:
            ax.scatter(
                anomaly_x, anomaly_y, color="red", s=10, zorder=3, label="Anomaly Point"
            )

        # Set plot labels and ticks
        tick_interval = max(1, all_len // 10)
        ax.set_xticks(range(0, all_len, tick_interval))
        x_label = [time_labels[i] for i in range(0, all_len, tick_interval)]
        ax.set_xticklabels(x_label, fontsize=10)
        ax.tick_params(axis="y", labelsize=10)
        ax.set_title(f"Node{node_id} - {field}", fontsize=12)
        ax.legend(fontsize=10)
        ax.grid(alpha=0.3)
        plt.tight_layout()

        # Save plot
        save_dir = os.path.join(self.plot_save_dir, str(node_id))
        os.makedirs(save_dir, exist_ok=True)
        timestamp_str = pd.Timestamp.now().strftime("%Y%m%d%H%M%S")
        plot_path = os.path.join(save_dir, f"{field}_{timestamp_str}.png")

        plt.savefig(plot_path, dpi=200, bbox_inches="tight")
        plt.close()

    @staticmethod
    def _generate_anomaly_info(
        node_id: int,
        recent_values: Dict[str, List[float]],
        current_values: Dict[str, List[float]],
        current_win_times: List[int],
        field_anomalies: Dict[str, Dict[str, List[int]]],
    ) -> List[AnomalyInfo]:
        """Generate AnomalyInfo objects from detection results"""
        anomalies = []

        for field, anomaly_dict in field_anomalies.items():
            for anomaly_type, timestamps in anomaly_dict.items():
                if not timestamps:
                    continue
                # Process timestamp ranges
                time_ranges = AlgorithmUtil.process_time_ranges(timestamps)
                recent_mean = float(np.mean(recent_values[field]))

                # Create AnomalyInfo for each time range
                for start_time, end_time in time_ranges:
                    start_idx = current_win_times.index(start_time)
                    end_idx = current_win_times.index(end_time)

                    # Calculate anomaly statistics
                    anomaly_values = current_values[field][start_idx : end_idx + 1]
                    anomaly_mean = float(np.mean(anomaly_values))
                    peak_val = np.max(anomaly_values)
                    trough_val = np.min(anomaly_values)

                    # Calculate change ratio relative to period mean
                    change_ratio = (
                        (anomaly_mean / recent_mean - 1) * 100
                        if recent_mean != 0
                        else 100
                    )

                    # Generate anomaly info based on type (upper/lower)
                    if anomaly_type == "upper":
                        description = f"increased {change_ratio:.2f}% ({recent_mean:.4f} -> {anomaly_mean:.4f}, peak: {peak_val:.4f})"
                    else:
                        description = f"decreased {change_ratio:.2f}% ({recent_mean:.4f} -> {anomaly_mean:.4f}, trough: {trough_val:.4f})"

                    # Append AnomalyInfo to result list
                    anomalies.append(
                        AnomalyInfo(node_id, start_time, end_time, field, description)
                    )

        return anomalies
