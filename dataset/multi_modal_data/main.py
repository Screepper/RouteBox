import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Optional

# Log type mapping: shortcut char -> directory/log type name
LOG_TYPE_MAP = {
    "S": "SystemNetwork",
    "B": "BGP",
    "R": "Router",
    "P": "Ping",
    "T": "Topology",
}

# Node type ranges
AS_NODE_RANGE = range(1, 51)  # 1-50: AS nodes (support S/B/R/P)
HOST_NODE_RANGE = range(51, 101)  # 51-100: Host nodes (support S/P only)


def parse_timestamp(ts_input: str) -> int:
    """
    Parse timestamp input: either "YYYY-MM-DD HH:MM:SS" string or 10-digit Unix timestamp.
    Returns Unix timestamp (int).
    """
    ts_input = ts_input.strip()

    # Check if it's a 10-digit timestamp
    if re.match(r"^\d{10}$", ts_input):
        return int(ts_input)

    # Try parsing as datetime string
    try:
        dt = datetime.strptime(ts_input, "%Y-%m-%d %H:%M:%S")
        return int(dt.timestamp())
    except ValueError:
        raise ValueError(
            f"Invalid timestamp format: '{ts_input}'. Expected 'YYYY-MM-DD HH:MM:SS' or 10-digit Unix timestamp."
        )


def parse_node_ids(ids_str: str) -> List[int]:
    """
    Parse node IDs from comma-separated string.
    """
    if not ids_str:
        return []

    ids = []
    for part in ids_str.split(","):
        part = part.strip()
        if not part:
            continue
        try:
            nid = int(part)
            if nid < 1 or nid > 100:
                raise argparse.ArgumentError(
                    None, f"Node ID must be between 1 and 100, got {nid}"
                )
            ids.append(nid)
        except ValueError:
            raise argparse.ArgumentError(None, f"Invalid node ID: '{part}'")
    return ids


def parse_log_types(lt_str: str) -> List[str]:
    """
    Parse log type string (e.g., 'SBRPT') into list of log type names.
    Only keeps valid characters S/B/R/P/T, case-insensitive, no duplicates.
    """
    if not lt_str:
        return []

    result: set = set()
    for char in lt_str.upper():
        if char in LOG_TYPE_MAP:
            result.add(LOG_TYPE_MAP[char])
    return list(result)


def get_file_time_range(date_str: str, hour_range: str) -> tuple:
    """
    Get start (inclusive) and end (exclusive) timestamp for a file based on its name.
    date_str: "YYYYMMDD"
    hour_range: "HH-HH" (e.g., "18-19" or "23-24")
    """
    from datetime import timedelta

    dt_start = datetime.strptime(date_str, "%Y%m%d")
    start_hour, end_hour = map(int, hour_range.split("-"))

    start_dt = dt_start.replace(hour=start_hour, minute=0, second=0, microsecond=0)

    # Handle end_hour == 24 (represents next day 00:00 as exclusive boundary)
    if end_hour == 24:
        end_dt = (dt_start + timedelta(days=1)).replace(
            hour=0, minute=0, second=0, microsecond=0
        )
    else:
        end_dt = dt_start.replace(hour=end_hour, minute=0, second=0, microsecond=0)

    return int(start_dt.timestamp()), int(end_dt.timestamp())


def load_json_file(filepath: Path) -> Optional[List[Dict]]:
    """
    Load JSON file. Returns list of records or None if file doesn't exist.
    Handles both list format and dict-with-'records' format.
    """
    if not filepath.exists():
        return None
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError as e:
        print(f"Warning: Failed to parse {filepath}: {e}", file=sys.stderr)
        return None


def filter_by_time_range(
    records: List[Dict], start_ts: int, end_ts: Optional[int]
) -> List[Dict]:
    """
    Filter records by timestamp range: [start_ts, end_ts) (end_ts exclusive, None means no upper bound).
    """
    result = []
    for rec in records:
        ts = rec.get("timestamp", 0)
        if ts < start_ts:
            continue
        if end_ts is not None and ts >= end_ts:
            continue
        result.append(rec)
    return result


def extract_global_topology(
    base_dir: Path, start_ts: int, end_ts: Optional[int]
) -> List[Dict]:
    """
    Extract global Topology logs within time range.
    Handles multi-file shards automatically.
    """
    topology_dir = base_dir / "global" / "Topology"
    if not topology_dir.exists():
        return []

    results = []
    # Iterate through all date directories (YYYYMMDD)
    for date_dir in sorted(topology_dir.iterdir()):
        if not date_dir.is_dir():
            continue
        date_str = date_dir.name

        # Iterate through all hourly files (HH-HH.json)
        for file_path in sorted(date_dir.glob("*.json")):
            hour_range = file_path.stem  # e.g., "18-19"

            # Check if file's time range overlaps with query range
            file_start, file_end = get_file_time_range(date_str, hour_range)

            # Skip if file is completely outside query range
            if end_ts is not None and file_start >= end_ts:
                continue
            if file_end <= start_ts:
                continue

            # Load and filter records
            records = load_json_file(file_path)
            if records:
                filtered = filter_by_time_range(records, start_ts, end_ts)
                results.extend(filtered)

    # Sort by timestamp ascending
    results.sort(key=lambda x: x.get("timestamp", 0))
    return results


def extract_node_logs(
    base_dir: Path,
    node_id: int,
    log_types: List[str],
    start_ts: int,
    end_ts: Optional[int],
) -> Dict[str, List[Dict]]:
    """
    Extract logs for a specific node within time range.
    Returns dict mapping normalized log_type_key -> list of records.
    """
    # Determine node type and available log types
    if node_id in AS_NODE_RANGE:
        node_type = "as"
        available_logs = ["SystemNetwork", "BGP", "Router", "Ping"]
    elif node_id in HOST_NODE_RANGE:
        node_type = "host"
        available_logs = ["SystemNetwork", "Ping"]
    else:
        return {}

    node_dir = base_dir / "nodes" / f"Node{node_id} ({node_type.upper()})"
    if not node_dir.exists():
        return {}

    results = {}

    for log_type in log_types:
        if log_type not in available_logs:
            continue

        log_dir = node_dir / log_type
        if not log_dir.exists():
            # Return empty array for requested but unavailable log type
            key = log_type.lower().replace("network", "_network")
            results[key] = []
            continue

        log_records = []

        # Iterate through date directories
        for date_dir in sorted(log_dir.iterdir()):
            if not date_dir.is_dir():
                continue
            date_str = date_dir.name

            # Iterate through hourly files
            for file_path in sorted(date_dir.glob("*.json")):
                hour_range = file_path.stem

                # Check time overlap
                file_start, file_end = get_file_time_range(date_str, hour_range)
                if end_ts is not None and file_start >= end_ts:
                    continue
                if file_end <= start_ts:
                    continue

                # Load and filter
                records = load_json_file(file_path)
                if records:
                    filtered = filter_by_time_range(records, start_ts, end_ts)
                    log_records.extend(filtered)

        # Sort by timestamp and store with normalized key
        log_records.sort(key=lambda x: x.get("timestamp", 0))
        key = log_type.lower().replace("network", "_network")
        results[key] = log_records

    return results


def extract_attacks(base_dir: Path, start_ts: int, end_ts: Optional[int]) -> List[Dict]:
    """
    Extract attack records within time range.
    A record matches if timestamp OR endTimestamp falls within [start_ts, end_ts).
    """
    attack_file = base_dir / "global" / "Attack" / "attack.json"
    if not attack_file.exists():
        return []

    records = load_json_file(attack_file)
    if not records:
        return []

    results = []
    for rec in records:
        ts = rec.get("timestamp", 0)
        end_ts_rec = rec.get("endTimestamp", ts)

        # Match if either timestamp falls in range [start_ts, end_ts)
        in_range_start = start_ts <= ts < (end_ts if end_ts else float("inf"))
        in_range_end = end_ts is not None and start_ts <= end_ts_rec < end_ts

        if in_range_start or in_range_end:
            results.append(rec)

    return results


def main():
    parser = argparse.ArgumentParser(
        description="RouteBox Multi-Modal Data Extractor: Extract logs within specified time range"
    )

    # Required: start time
    parser.add_argument(
        "-st",
        "--startTime",
        type=str,
        required=True,
        help="Start time: 'YYYY-MM-DD HH:MM:SS' or 10-digit Unix timestamp",
    )

    # Optional: end time
    parser.add_argument(
        "-et",
        "--endTime",
        type=str,
        default=None,
        help="End time (optional): same format as startTime. If not specified, extract all logs from startTime onwards",
    )

    # Required: log types
    parser.add_argument(
        "-lt",
        "--logType",
        type=str,
        required=True,
        help="Log types string containing any of S/B/R/P/T (e.g., 'SBRPT', 'ST', 'BST'). S=SystemNetwork, B=BGP, R=Router, P=Ping, T=Topology",
    )

    # Optional: node IDs
    parser.add_argument(
        "-ids",
        "--nodeIds",
        type=str,
        default="",
        help="Comma-separated node IDs (1-50: AS nodes, 51-100: Host nodes). Example: '2,57,100'",
    )

    # Dataset base directory
    parser.add_argument(
        "-d",
        "--dataDir",
        type=str,
        default="./multi_modal_routing_system_data",
        help="Base directory of the dataset (default: 'multi_modal_routing_system_data')",
    )

    args = parser.parse_args()

    # Parse and validate inputs
    try:
        start_ts = parse_timestamp(args.startTime)
    except ValueError as e:
        parser.error(str(e))

    end_ts = None
    if args.endTime:
        try:
            end_ts = parse_timestamp(args.endTime)
            if end_ts <= start_ts:
                parser.error(
                    f"endTime ({args.endTime}) must be greater than startTime ({args.startTime})"
                )
        except ValueError as e:
            parser.error(str(e))

    log_types = parse_log_types(args.logType)
    if not log_types:
        parser.error(
            f"No valid log types found in '{args.logType}'. Valid characters: S, B, R, P, T"
        )

    node_ids = parse_node_ids(args.nodeIds)

    base_dir = Path(args.dataDir)
    if not base_dir.exists():
        parser.error(f"Dataset directory not found: {base_dir}")

    # ========== Extraction ==========
    print(f"Extracting logs from {args.startTime}", end="")
    if end_ts:
        print(f" to {args.endTime}", end="")
    print(f" | Log types: {args.logType}", end="")
    if node_ids:
        print(f" | Nodes: {node_ids}", end="")
    print()

    # 1. Extract attacks (always included in output)
    attacks = extract_attacks(base_dir, start_ts, end_ts)
    print(f"  ✓ Found {len(attacks)} attack records")

    # 2. Extract global topology (if T in log types)
    topology_logs = []
    if "Topology" in log_types:
        topology_logs = extract_global_topology(base_dir, start_ts, end_ts)
        print(f"  ✓ Loaded {len(topology_logs)} topology records")

    # 3. Extract node logs
    node_results = {"as": {}, "host": {}}

    for nid in node_ids:
        node_type = "as" if nid in AS_NODE_RANGE else "host"
        logs = extract_node_logs(base_dir, nid, log_types, start_ts, end_ts)

        if logs:  # Only include nodes with at least one log type extracted
            node_results[node_type][str(nid)] = logs
            count = sum(len(v) for v in logs.values())
            print(f"  ✓ Node {nid} ({node_type.upper()}): {count} total records")

    # ========== Build output structure ==========
    output = {
        "startTime": start_ts,
        "endTime": end_ts if end_ts is not None else -1,  # -1 indicates no upper bound
        "global": {"attacks": attacks, "topology": topology_logs},
        "nodes": {"as": node_results["as"], "host": node_results["host"]},
    }

    # Output JSON to stdout
    print("\n" + "=" * 60)
    print("Extraction complete. Output JSON structure:")
    print("=" * 60)
    print(json.dumps(output, indent=2, ensure_ascii=False))

    # Also save to file
    output_file = f"extracted_{start_ts}_{end_ts or 'open'}.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False)
    print(f"\n Saved to {output_file}")


if __name__ == "__main__":
    main()
