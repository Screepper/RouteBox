import argparse
import json
import os
import re
from datetime import datetime
from pathlib import Path

from dotenv import load_dotenv

from LLM_call import call

# ==================== Scoring ====================
NON_ATTACKER_ATTACK_TYPES = {
    "Node Down",
    "System Overload",
    "Memory Leakage",
    "Link Down",
    "Link Congestion",
    "Link Jitter",
}

ATTACK_SCORE_RULES = {
    "Prefix Hijacking": [
        ("^prefix.*hijack.*", 30),
        (".*hijack.*prefix.*", 25),
        (".*prefix.*hijack.*", 25),
        (".*hijack.*", 20),
        (".*prefix.*", 15),
        (".*rout.*", 10),
        (".*link.*", 5),
    ],
    "Sub-prefix Hijacking": [
        (".*sub.*prefix.*hijack.*", 30),
        (".*hijack.*prefix.*", 25),
        (".*prefix.*hijack.*", 25),
        (".*hijack.*", 20),
        (".*prefix.*", 15),
        (".*rout.*", 10),
        (".*link.*", 5),
    ],
    "Route Flapping": [
        (".*route.*flap.*", 30),
        (".*flap.*rout.*", 25),
        (".*flap.*", 15),
        (".*oscillat.*", 15),
        (".*shock.*", 15),
        (".*rout.*", 10),
        (".*link.*", 5),
    ],
    "Route Leakage": [
        ("^route.*leak.*", 30),
        (".*leak.*", 25),
        (".*rout.*", 10),
        (".*link.*", 5),
    ],
    "Node Down": [
        (".*node.*down.*", 30),
        (".*node.*failure.*", 30),
        (".*down.*", 20),
        (".*failure.*", 20),
        (".*broken.*", 15),
        (".*break.*", 15),
        (".*system.*", 5),
        (".*link.*", 5),
    ],
    "System Overload": [
        (".*system.*over.*", 30),
        (".*overload.*", 25),
        (".*system.*", 15),
        (".*node.*", 10),
        (".*ddos.*", 10),
        (".*distribut.*", 10),
        (".*jitter.*", 5),
        (".*link.*", 5),
    ],
    "Memory Leakage": [
        (".*memory.*leak.*", 30),
        (".*memory.*drain.*", 25),
        (".*over.*", 15),
        (".*system.*", 15),
        (".*node.*", 10),
        (".*ddos.*", 10),
        (".*distribut.*", 10),
        (".*jitter.*", 5),
        (".*link.*", 5),
    ],
    "Link Down": [
        (".*link.*down.*", 30),
        (".*link.*failure.*", 30),
        (".*down.*", 20),
        (".*failure.*", 20),
        (".*link.*", 15),
        (".*broken.*", 15),
        (".*break.*", 15),
        (".*node.*", 10),
        (".*system.*", 5),
    ],
    "Link Congestion": [
        (".*link.*congest.*", 30),
        (".*latency.*jitter.*", 25),
        (".*ddos.*", 25),
        (".*distribut.*", 25),
        (".*congest.*", 25),
        (".*jitter.*", 20),
        (".*link.*", 15),
        (".*node.*", 10),
        (".*system.*", 5),
    ],
    "Link Jitter": [
        (".*link.*jitter.*", 30),
        (".*link.*congest.*", 25),
        (".*ddos.*", 25),
        (".*distribut.*", 25),
        (".*jitter.*", 25),
        (".*congest.*", 20),
        (".*link.*", 15),
        (".*node.*", 10),
        (".*system.*", 5),
    ],
}


def _parse_ts(ts_val):
    """Parse timestamp string to int timestamp."""
    try:
        return int(
            datetime.strptime(str(ts_val).strip(), "%Y-%m-%d %H:%M:%S").timestamp()
        )
    except Exception:
        return 0


def calculate_type_score(real_type, pred_type):
    """Calculate attack type score."""
    if not pred_type:
        return 0
    rules = ATTACK_SCORE_RULES.get(real_type.strip(), [])
    max_score = 0
    for pattern, score in rules:
        if re.fullmatch(pattern, pred_type, re.IGNORECASE):
            max_score = max(max_score, score)
    return max_score


def calculate_attacker_score(real_type, real_attackers, pred_attackers):
    """Calculate attacker score."""
    real_set = set(real_attackers or [])
    pred_set = set(pred_attackers or [])
    if real_type in NON_ATTACKER_ATTACK_TYPES:
        return 20 if len(pred_set) == 0 else 10
    if len(real_set) == 0 and len(pred_set) == 0:
        return 20
    if len(real_set) == 0 or len(pred_set) == 0:
        return 0
    return int((len(real_set & pred_set) * 20) / len(real_set))


def calculate_victim_score(real_type, real_victims, pred_attackers, pred_victims):
    """Calculate victim score."""
    pred_v_set = set(pred_victims or [])
    if real_type in NON_ATTACKER_ATTACK_TYPES:
        pred_v_set.update(set(pred_attackers or []))
    if real_type == "Route Flapping":
        return 20
    real_v_set = set(real_victims or [])
    if len(real_v_set) == 0 and len(pred_v_set) == 0:
        return 20
    if len(real_v_set) == 0 or len(pred_v_set) == 0:
        return 0
    return int((len(real_v_set & pred_v_set) * 20) / len(real_v_set))


def calculate_time_score(real_start, real_end, pred_start_str, pred_end_str):
    """Calculate time score."""
    pred_start = _parse_ts(pred_start_str)
    pred_end = _parse_ts(pred_end_str)
    if (
        real_start <= 0
        or real_end <= 0
        or pred_start <= 0
        or pred_end <= 0
        or real_start >= real_end
    ):
        return 0
    delta_start = abs(pred_start - real_start)
    delta_end = abs(pred_end - real_end)
    total_delta = delta_start + delta_end
    MIN_DELTA = 120  # 2 * 60
    MAX_DELTA = 2400  # 20 * 120
    if total_delta <= MIN_DELTA:
        return 30
    return int(30 - 30 * min(1.0, total_delta / MAX_DELTA))


def main():
    VALID_DS = [
        "S",
        "SBP",
        "SBRT",
        "SBR",
        "SBRPT",
        "SBRPT_no_prompt",
        "SBPT",
        "SRT",
        "SPT",
        "ST",
    ]
    parser = argparse.ArgumentParser(description="RouteBox RCA Script")
    parser.add_argument(
        "-ds",
        "--dataSource",
        type=str,
        default="SBRPT",
        help=f"Data source config ({', '.join(VALID_DS)})",
    )
    parser.add_argument(
        "-ec",
        "--evalCount",
        type=int,
        default=268,
        help="Max number of evaluations to run (default: 268)",
    )
    parser.add_argument(
        "-at",
        "--attackType",
        type=str,
        default=None,
        help="Filter by specific attack type (e.g., 'Prefix Hijacking')",
    )
    args = parser.parse_args()

    if args.dataSource not in VALID_DS:
        parser.error(
            f"Invalid dataSource '{args.dataSource}'. Must be one of {VALID_DS}"
        )

    load_dotenv("../.env")
    if (
        not os.getenv("API_KEY")
        or not os.getenv("BASE_URL")
        or not os.getenv("API_MODEL")
    ):
        raise ValueError("Missing API_KEY, BASE_URL or API_MODEL in .env file.")

    # Load Ground Truth
    gt_path = Path("groundtruth.json")
    if not gt_path.exists():
        raise FileNotFoundError(f"{gt_path} not found.")
    with open(gt_path, "r", encoding="utf-8") as f:
        gt_list = json.load(f)
    gt_dict = {item["timeId"]: item for item in gt_list}

    data_dir = Path(f"question/{args.dataSource}")

    files = sorted(
        [f for f in data_dir.iterdir() if f.suffix == ".txt" and f.stem.isdigit()]
    )
    if not files:
        print("No valid prompt files found in the specified directory.")
        return

    results = []
    target_count = args.evalCount
    success_count = 0
    skip_count = 0
    processed_count = 0
    print(
        f"Starting RCA evaluation with dataSource={args.dataSource}, maxCount={target_count}, filterType={args.attackType}"
    )

    for file_path in files:
        time_id = int(file_path.stem)
        gt = gt_dict.get(time_id)
        if args.attackType and gt["attackType"].strip() != args.attackType.strip():
            continue

        processed_count += 1
        if processed_count > target_count:
            break

        print(
            f"[{processed_count}/{target_count}] Evaluating {file_path.name} (GT Type: {gt['attackType']})"
        )
        prompt = file_path.read_text(encoding="utf-8")
        # Call LLM
        response = call(prompt, enable_thinking=False, output=False, stream=False)
        # response = call(prompt, enable_thinking=False, output=True, stream=True)

        if not response:
            print(f"LLM call failed for {time_id}, skip.")
            skip_count += 1
            continue

        # Parse JSON from LLM response
        try:
            res_json = json.loads(response)
        except json.JSONDecodeError:
            print(
                f"Failed to parse JSON from LLM response for {time_id}. Response snippet: {response[:100]}..."
            )
            skip_count += 1
            continue

        # Extract prediction fields
        pred_type = res_json.get("attackType", "")
        pred_attackers = res_json.get("attackers", [])
        pred_victims = res_json.get("victims", [])
        pred_start = res_json.get("anomalyStart", "")
        pred_end = res_json.get("anomalyEnd", "")

        # Scoring
        st = calculate_type_score(gt["attackType"], pred_type)
        sa = calculate_attacker_score(gt["attackType"], gt["attackers"], pred_attackers)
        sv = calculate_victim_score(
            gt["attackType"], gt["victims"], pred_attackers, pred_victims
        )
        stl = calculate_time_score(gt["startTime"], gt["endTime"], pred_start, pred_end)
        total = st + sa + sv + stl

        results.append(
            {
                "timeId": time_id,
                "groundtruth": gt,
                "scores": {"ST": st, "SA": sa, "SV": sv, "STL": stl, "TS": total},
            }
        )
        success_count += 1

    if not results:
        return

    # Group by attack type
    type_stats = {}
    for r in results:
        t = r["groundtruth"]["attackType"]
        if t not in type_stats:
            type_stats[t] = {"count": 0, "ST": 0, "SA": 0, "SV": 0, "STL": 0, "TS": 0}
        type_stats[t]["count"] += 1
        for k in type_stats[t]:
            if k != "count":
                type_stats[t][k] += r["scores"][k]

    # Calculate global average
    total_count = len(results)
    avg_scores = {
        k: sum(r["scores"][k] for r in results) / total_count
        for k in ["ST", "SA", "SV", "STL", "TS"]
    }

    print("=" * 95)
    print(
        f"| {'Attack Type':<20} | {'#Samples':<8} | {'ST(30)':<8} | {'SA(20)':<8} | {'SV(20)':<8} | {'STL(30)':<8} | {'TS(100)':<10} |"
    )
    print("-" * 95)
    for t, stats in type_stats.items():
        c = stats["count"]
        print(
            f"| {t:<20} | {c:<8} | {stats['ST'] / c:<8.2f} | {stats['SA'] / c:<8.2f} | {stats['SV'] / c:<8.2f} | {stats['STL'] / c:<8.2f} | {stats['TS'] / c:<10.2f} |"
        )
    print("=" * 95)
    print(
        f"Global Average (N={total_count}): ST={avg_scores['ST']:.2f}, SA={avg_scores['SA']:.2f}, SV={avg_scores['SV']:.2f}, STL={avg_scores['STL']:.2f}, TS={avg_scores['TS']:.2f}"
    )
    print(
        f"Processed: {success_count}, Valid: {total_count}, Skipped/Parsing Error: {skip_count}"
    )
    print("=" * 95)


if __name__ == "__main__":
    main()
