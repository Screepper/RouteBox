import os
import json
from datetime import datetime, timezone
from collections import defaultdict
from typing import List, Tuple, Dict, DefaultDict, Union
import ipaddress
import mysql.connector
import pytricia
import time

# MySQL Database Configuration
DB_CONFIG = {
    "host": "localhost",
    "user": "user",
    "password": "password",
    "database": "artemis",
}

BGP_DATA_FOLDER = "./jsons"  # JSON files folder
BULK_INSERT_SIZE = 1000  # Bulk insert size for MySQL database
PROGRESS_INTERVAL = 10000  # Progress display interval


def parse_bgp_json_entry(entry: dict, entry_num: int) -> List[dict]:
    """
    Parse JSON entry of BGP data record
    nodeId, timestamp, updateType, peerIp, peerAs, ipPrefix, asPath, origin, nextHop

    Returns:
        List of parsed BGP announcements (each announcement separately)
    """
    parsed_entries = []

    try:
        timestamp = entry.get("timestamp")
        if not timestamp:
            return None

        timestamp_dt = datetime.fromtimestamp(timestamp, tz=timezone.utc)

        peer_asn = entry.get("peerAs")
        if peer_asn is not None:
            peer_asn = int(peer_asn)

        as_path_raw = entry.get("asPath", "")
        if isinstance(as_path_raw, str) and as_path_raw.strip():
            as_path = [int(x) for x in as_path_raw.strip().split()]
        elif isinstance(as_path_raw, list):
            as_path = as_path_raw
        else:
            as_path = []

        update_type = entry.get("updateType", "advertise")
        if update_type == "withdraw":
            return None

        prefix = entry.get("ipPrefix")
        if prefix:
            parsed_entries.append(
                {
                    "timestamp": timestamp_dt,
                    "prefix": prefix,
                    "as_path": as_path,
                    "peer_asn": peer_asn,
                    "peer_ip": entry.get("peerIp"),
                }
            )

        return parsed_entries

    except (ValueError, KeyError, TypeError) as e:
        if entry_num % 10000 == 0:
            print(f"[JSON parse error] Entry {entry_num}: {e}")
        return None


def clean_as_path(as_path: List[int]) -> List[int]:
    """
    Clean AS_PATH
    Example: [123, 456, 456, 789] -> [123, 456, 789]
    """
    cleaned = []
    for asn in as_path:
        if not cleaned or cleaned[-1] != asn:  # Remove duplicates
            cleaned.append(asn)
    return cleaned


def load_prefix_trie(cursor) -> Tuple[pytricia.PyTricia, pytricia.PyTricia]:
    """
    Load valid prefix-AS mappings from database, and build IPv4/IPv6 prefix trees
    Returns: (pt_v4, pt_v6)
    """
    print("Loading prefix-AS mapping from database...")
    pt_v4 = pytricia.PyTricia(32)
    pt_v6 = pytricia.PyTricia(128)

    cursor.execute("SELECT prefix, asn FROM prefix_as_mapping")
    count = 0
    for prefix, asn in cursor.fetchall():
        try:
            ip_net = ipaddress.ip_network(prefix)
            pt = pt_v4 if ip_net.version == 4 else pt_v6
            prefix_str = str(ip_net)
            if prefix_str in pt:
                if isinstance(pt[prefix_str], set):
                    pt[prefix_str].add(asn)
                else:
                    pt[prefix_str] = {asn}
            else:
                pt[prefix_str] = {asn}
            count += 1
            if count % 100000 == 0:
                print(f"  Loaded {count} prefix mappings...")
        except ValueError as e:
            print(f"[Invalid prefix] Invalid prefix {prefix}: {e}")
    print(f"Loaded total {len(pt_v4)} IPv4 prefixes and {len(pt_v6)} IPv6 prefixes.")
    return pt_v4, pt_v6


def load_prepend_patterns_from_db(cursor) -> DefaultDict[str, List[List[int]]]:
    """
    Load prepend patterns from database
    Returns: {prefix: [prepend_seq]}
    """
    print("Loading prepend patterns from database...")
    cursor.execute("SELECT prefix, prepend_seq FROM prepend_patterns")

    prefix_patterns = defaultdict(list)
    count = 0
    for prefix, path_json in cursor.fetchall():
        try:
            path = json.loads(path_json)
            if isinstance(path, list):
                prefix_patterns[prefix].append(path)
                count += 1
                if count % 10000 == 0:
                    print(f"  Loaded {count} prepend patterns...")
        except Exception as e:
            print(f"[Parse error] prefix={prefix}, path={path_json}: {e}")
    print(f"Loaded total {len(prefix_patterns)} prefixes with prepend patterns.")
    return prefix_patterns


def detect_type_0_hijack(
    prefix: str, origin_asn: int, pt_v4: pytricia.PyTricia, pt_v6: pytricia.PyTricia
) -> bool:
    """
    Detect Type 0 Hijack for prefix
    Returns: True=Hijack, False=legal
    """
    try:
        ip_net = ipaddress.ip_network(prefix)
        pt = pt_v4 if ip_net.version == 4 else pt_v6
        prefix_str = str(ip_net)

        if prefix_str in pt:
            return origin_asn not in pt[prefix_str]  # AS mismatch indicates hijack
        return False  # Prefix not registered, not checked

    except Exception as e:
        # Ignore errors for now
        return False


def detect_type_1_hijack(as_path: List[int], cursor) -> bool:
    """
    Detect Type 1 Hijack for AS_PATH

    Args:
        as_path: Cleaned AS_PATH (e.g. [AS1, AS2, AS3])

    Returns:
        True: Hijack detected (first hop AS not related to origin AS)
        False: Legal
    """
    if len(as_path) < 2:
        return False  # Single AS path, not checked

    first_hop = as_path[-2]  # First hop AS
    origin_as = as_path[-1]  # Origin AS

    cursor.execute(
        """SELECT 1 FROM as_relationship 
           WHERE ((asn1 = %s AND asn2 = %s) OR
                  (asn1 = %s AND asn2 = %s))
        LIMIT 1""",
        (first_hop, origin_as, origin_as, first_hop),
    )

    # No result means no valid relationship
    return cursor.fetchone() is None


def detect_type_p_hijack(
    as_path: List[int], prepend_patterns: List[List[int]]
) -> Tuple[int, str]:
    """
    Detect Type-P Hijack for AS_PATH

    Args:
        as_path: Cleaned AS_PATH (e.g. [AS1, AS2, AS3])
        prepend_patterns: List of prepend patterns (e.g. [[AS1, AS2], [AS3]])

    Returns:
        (-1, "-"): Legal or no match data
        (ASN, "P"): Suspicious Type-P hijack, ASN is the first non-matching AS
    """
    if not as_path or not prepend_patterns:
        return -1, "-"

    orig_path = as_path
    best_match_len = 0
    hijacker = -1

    for pattern in prepend_patterns:
        if len(orig_path) < len(pattern) + 1:
            continue
        segment = orig_path[-len(pattern) - 1 : -1]  # Exclude origin AS

        if segment == pattern:
            return -1, "-"  # Legal path

        # Calculate match length
        match_len = 0
        for a, b in zip(segment[::-1], pattern[::-1]):
            if a == b:
                match_len += 1
            else:
                break

        if match_len > best_match_len:
            best_match_len = match_len
            hijacker = (
                as_path[-best_match_len - 1] if best_match_len < len(as_path) else -1
            )

    if hijacker != -1:
        return hijacker, "P"
    return -1, "-"


def detect_prefix_squatting_or_subprefix(
    prefix: str, origin_asn: int, pt_v4: pytricia.PyTricia, pt_v6: pytricia.PyTricia
) -> Tuple[str, Union[str, None]]:
    """
    Detect prefix squatting or subprefix hijack
    Returns:
        ("REGISTERED", None): Registered prefix
        ("SUBPREFIX", supernet): Subprefix hijack
        ("SQUATTING", None): Prefix squatting
        ("ERROR", None): Error case
    """
    try:
        ip_net = ipaddress.ip_network(prefix)
        pt = pt_v4 if ip_net.version == 4 else pt_v6
        prefix_str = str(ip_net)

        if prefix_str in pt:
            return "REGISTERED", None

        # Find the most specific matching prefix (supernet)
        supernet = None
        for net in pt:
            try:
                if ipaddress.ip_network(net).supernet_of(ip_net):
                    if supernet is None or len(net.split("/")[1]) > len(
                        supernet.split("/")[1]
                    ):
                        supernet = net
            except:
                continue

        if supernet:
            return "SUBPREFIX", supernet

        return "SQUATTING", None
    except Exception:
        return "ERROR", None


def parse_bgp_json_file(
    json_file_path: str,
    pt_v4: pytricia.PyTricia,
    pt_v6: pytricia.PyTricia,
    prepend_dict: Dict[str, List[List[int]]],
    db_cursor,
    conn,
) -> None:
    """Parse BGP data file and detect hijacks events."""
    print(f"\nProcessing file: {json_file_path}")
    file_size = os.path.getsize(json_file_path)
    print(f"File size: {file_size / 1024 / 1024:.2f} MB")

    hijack_records = []
    processed_count = 0
    skipped_count = 0
    total_entries = 0
    start_time = time.time()

    try:
        with open(json_file_path, "r", encoding="utf-8") as file:
            print("Loading JSON data...")
            data = json.load(file)

            if not isinstance(data, list):
                print(f"[Error] JSON file format error, expect array format")
                return

            total_entries = len(data)
            print(f"Total entries to process: {total_entries}")
            print("Processing...")

            for entry_num, entry in enumerate(data, 1):
                if entry_num % PROGRESS_INTERVAL == 0:
                    elapsed = time.time() - start_time
                    rate = entry_num / elapsed if elapsed > 0 else 0
                    eta = (total_entries - entry_num) / rate if rate > 0 else 0
                    print(
                        f"  Progress: {entry_num}/{total_entries} ({entry_num / total_entries * 100:.1f}%) | "
                        f"Processing speed: {rate:.0f} entries/s | "
                        f"Processed B records: {processed_count} | "
                        f"Remaining time: {eta / 60:.1f} minutes"
                    )

                # Parse JSON entry
                parsed_entries = parse_bgp_json_entry(entry, entry_num)
                if not parsed_entries:
                    skipped_count += 1
                    continue

                for bgp_data in parsed_entries:
                    try:
                        timestamp = bgp_data["timestamp"]
                        prefix = bgp_data["prefix"]
                        as_path = bgp_data["as_path"]

                        # Clean AS path
                        cleaned_as_path = clean_as_path(as_path)
                        if not cleaned_as_path:
                            continue
                        origin_asn = cleaned_as_path[-1]

                        processed_count += 1

                        # Step 1: Subprefix/Squatting detection
                        prefix_type, supernet = detect_prefix_squatting_or_subprefix(
                            prefix, origin_asn, pt_v4, pt_v6
                        )
                        if prefix_type == "SUBPREFIX":
                            hijack_records.append(
                                (
                                    prefix,
                                    origin_asn,
                                    f"Subprefix Hijack (supernet: {supernet})",
                                    timestamp,
                                )
                            )
                            continue
                        elif prefix_type == "SQUATTING":
                            hijack_records.append(
                                (prefix, origin_asn, "Prefix Squatting", timestamp)
                            )
                            continue
                        elif prefix_type == "ERROR":
                            continue

                        # Step 2: Type-0 Hijack
                        if detect_type_0_hijack(prefix, origin_asn, pt_v4, pt_v6):
                            hijack_records.append(
                                (prefix, origin_asn, "Origin Hijack", timestamp)
                            )
                            continue

                        # Step 3: Type-1 Hijack
                        if len(cleaned_as_path) >= 2:
                            is_type1 = detect_type_1_hijack(cleaned_as_path, db_cursor)
                            if is_type1:
                                hijack_records.append(
                                    (
                                        prefix,
                                        origin_asn,
                                        "Path Fabrication Hijack",
                                        timestamp,
                                    )
                                )
                                continue

                        # Step 4: Type-P Hijack (prefix match)
                        for pattern_prefix, patterns in prepend_dict.items():
                            if prefix.startswith(pattern_prefix.rstrip("/")):
                                hijacker, p_type = detect_type_p_hijack(
                                    cleaned_as_path, patterns
                                )
                                if p_type == "P":
                                    hijack_records.append(
                                        (
                                            prefix,
                                            origin_asn,
                                            f"Type-P Hijack (suspected AS: {hijacker})",
                                            timestamp,
                                        )
                                    )
                                    break

                        # Batch insert logic
                        if len(hijack_records) >= BULK_INSERT_SIZE:
                            db_cursor.executemany(
                                """INSERT INTO hijack_events
                                   (prefix, origin_asn, hijack_type, timestamp) 
                                   VALUES (%s, %s, %s, %s)""",
                                hijack_records,
                            )
                            conn.commit()
                            print(f"  ✓ Inserted {len(hijack_records)} hijack records")
                            hijack_records = []

                    except Exception as e:
                        continue

        # Insert remaining records
        if hijack_records:
            db_cursor.executemany(
                """INSERT INTO hijack_events
                   (prefix, origin_asn, hijack_type, timestamp) 
                   VALUES (%s, %s, %s, %s)""",
                hijack_records,
            )
            conn.commit()

        elapsed = time.time() - start_time
        print(f"\nFile processing complete!")
        print(f"  - Processing time: {elapsed:.2f} seconds")
        print(f"  - Total BGP entries: {total_entries}")
        print(f"  - Valid BGP records: {processed_count}")
        print(f"  - Skipped records: {skipped_count}")
        print(f"  - Detected hijacks: {processed_count - len(hijack_records)} records")

    except json.JSONDecodeError as e:
        print(f"[JSON parse error] {json_file_path}: {e}")
    except Exception as e:
        print(f"[File parse error] {json_file_path}: {e}")
        import traceback

        traceback.print_exc()


def parse_all_json_files(
    folder_path: str,
    pt_v4: pytricia.PyTricia,
    pt_v6: pytricia.PyTricia,
    prepend_dict: Dict[str, List[List[int]]],
    db_cursor,
    conn,
) -> None:
    """
    Parse all .json files in a folder as BGP data files in the database
    """
    print(f"Loading {len(json_files)} .json files from folder {folder_path}...")
    json_files = [f for f in os.listdir(folder_path) if f.endswith(".json")]

    if not json_files:
        print(f"Folder {folder_path} is empty.")
        return

    print(f"Found {len(json_files)} .json files to process.")

    for idx, file_name in enumerate(sorted(json_files), 1):
        print(f"\n{'=' * 60}")
        print(f"Processing file [{idx}/{len(json_files)}]: {file_name}")
        print(f"{'=' * 60}")
        json_path = os.path.join(folder_path, file_name)
        parse_bgp_json_file(json_path, pt_v4, pt_v6, prepend_dict, db_cursor, conn)


def main() -> None:
    start = datetime.now()
    print(f"Program start time: {start}")
    print(f"{'=' * 60}")

    print("Connecting to database...")
    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor(buffered=True)
    print("Database connection successful!")

    try:
        # Load prefix-AS mapping data from database
        pt_v4, pt_v6 = load_prefix_trie(cursor)
        # Load prepend patterns from database
        prepend_dict = load_prepend_patterns_from_db(cursor)
        # Parse BGP data files
        parse_all_json_files(BGP_DATA_FOLDER, pt_v4, pt_v6, prepend_dict, cursor, conn)

        print(f"\n{'=' * 60}")
        print("All files processed successfully!")

    except Exception as e:
        print(f"[Program error] {e}")
        import traceback

        traceback.print_exc()
    finally:
        cursor.close()
        conn.close()
        print("Database connection closed.")
        end = datetime.now()
        run_time = end - start
        print(f"Run time: {run_time}")
        print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
