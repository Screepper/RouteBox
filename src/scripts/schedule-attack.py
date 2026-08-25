import requests
import time
import json
import sys
import logging
import random
import os

LOG_DIR = "../logs"

os.makedirs(LOG_DIR, exist_ok=True)

ROUTEBOX_SERVICE_URL = "http://127.0.0.1:12101"  # "http://ip:port"  # your ROUTEBOX service url, e.g. http://127.0.0.1:12100

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s-%(levelname)s- %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[
        # logging.StreamHandler(),
        logging.FileHandler(
            os.path.join(LOG_DIR, "schedule_attack.log"), encoding="utf-8"
        )
    ],
)
logger = logging.getLogger("file_logger")

attack_urls = [
    f"{ROUTEBOX_SERVICE_URL}/attack/bgp",
    f"{ROUTEBOX_SERVICE_URL}/attack/node",
    f"{ROUTEBOX_SERVICE_URL}/attack/link",
]
FORCE_REVOKE_URL = f"{ROUTEBOX_SERVICE_URL}/attack/forceRevoke"

BASE_ATTACK_DURATION = 60 * 8  # 1 attack duration
JITTER = 60 * 2  # jitter between attacks
BASE_INTERVAL = 60 * 30  # base interval between attacks (30m)

HEADERS = {"Content-Type": "application/json", "User-Agent": "Tester"}

attack_nums = [1, 2]
probabilities = [0.9, 0.11]

attack_types = [
    {"name": "Prefix Hijacking", "url": f"{attack_urls[0]}/0"},
    {"name": "Sub-prefix Hijacking", "url": f"{attack_urls[0]}/1"},
    # {"name": "Route Flapping", "url": f"{attack_urls[0]}/5"},
    {"name": "Node Down", "url": f"{attack_urls[1]}/0"},
    # {"name": "System Overload", "url": f"{attack_urls[0]}/1"},
    # {"name": "Memory Leak", "url": f"{attack_urls[0]}/2"},
    # {"name": "Link Down", "url": f"{attack_urls[2]}/0"},
    # {"name": "Link Congestion", "url": f"{attack_urls[2]}/1"},
    # {"name": "Link Jitter", "url": f"{attack_urls[2]}/2"},
]

node_ids = [i for i in range(1, 101)]
as_node_ids = [i for i in range(1, 51)]
link_ids = [i for i in range(1, 63)]

BGP_DATA = {
    "attackerId": 0,
    "targetIds": [],
}
NODE_DATA = {"targetIds": []}
LINK_DATA = {"targetIds": []}


def attack_restore(is_retry: bool = False) -> bool:
    try:
        logger.info(f"Start restoring attack")
        if is_retry:
            time.sleep(2)
            logger.info("Retrying restore request...")

        response = requests.get(FORCE_REVOKE_URL, headers=HEADERS, timeout=60)

        json_response = response.json()
        if response.status_code == 200:
            logger.info(f"Attack restored successfully")
            return True
        elif not is_retry:
            logger.error(f"Restore request failed, response content: {json_response}")
            return attack_restore(True)
        else:
            logger.error(f"Consecutive restore requests failed, giving up")
            return False

    except Exception as e:
        if not is_retry:
            logger.error(f"Restore request exception: {str(e)}")
            return attack_restore(True)
        else:
            logger.error(f"Consecutive restore requests exception: {str(e)}")
            return False


def attack() -> bool:
    # Randomly select attack type
    attack_index = random.randint(0, len(attack_types) - 1)
    attack_name = attack_types[attack_index]["name"]

    try:
        logger.info(f"Start [{attack_name}] attack")
        # Randomly select attack number
        attack_num = random.choices(attack_nums, weights=probabilities)[0]
        # Prepare attack data based on attack type
        if 0 <= attack_index <= 1:  # BGP-like attacks
            # Randomly select attacker
            attacker_id = random.sample(as_node_ids, 1)[0]
            # Exclude attacker from candidate targets
            candidate_targets = [
                node_id for node_id in as_node_ids if node_id != attacker_id
            ]
            # Randomly select targets
            target_ids = random.sample(candidate_targets, attack_num)
            # Update BGP attack data
            BGP_DATA["attackerId"] = attacker_id
            BGP_DATA["targetIds"] = target_ids
            post_data = json.dumps(BGP_DATA)
            logger.info(f"Attacker: {attacker_id}, Targets: {target_ids}")
        elif attack_index == 2:  # Node-like attacks
            # Randomly select targets
            target_ids = random.sample(node_ids, attack_num)
            NODE_DATA["targetIds"] = target_ids
            post_data = json.dumps(NODE_DATA)
            logger.info(f"Targets: {target_ids}")
        else:  # Link-like attacks
            # Randomly select targets
            target_links = random.sample(link_ids, attack_num)
            LINK_DATA["targetIds"] = target_links
            post_data = json.dumps(LINK_DATA)
            logger.info(f"Targets: {target_links}")
        # Send attack request
        response = requests.post(
            attack_types[attack_index]["url"],
            data=post_data,
            headers=HEADERS,
            timeout=60,
        )
        json_response = response.json()
        if response.status_code == 200:
            logger.info("Attack successful")
            return True
        else:
            logger.error(f"Attack failed, response content: {json_response}")
            return False

    except Exception as e:
        logger.error(f"Exception when attacking [{attack_name}]: {str(e)}")
        return False


if __name__ == "__main__":
    try:
        logger.info("Start attack process (average 1h 1 attack, 15min duration)...")
        while True:
            if not attack():
                logger.error("Attack initiation failed, waiting for next cycle")
                time.sleep(BASE_INTERVAL)
                continue
            # Wait for base duration + random jitter
            attack_duration = BASE_ATTACK_DURATION + random.randint(-JITTER, JITTER)
            logger.info(f"Attack duration: {attack_duration} seconds")
            time.sleep(attack_duration)
            # Restore attack
            if not attack_restore():
                logger.error("Attack restoration failed, waiting for next cycle")
            # Wait for attack interval time
            logger.info(f"Wait {BASE_INTERVAL} seconds for next attack")
            time.sleep(BASE_INTERVAL)

    except Exception as e:
        logger.critical(f"Program error: {str(e)}", exc_info=True)
        # Try to restore current attack
        attack_restore()
        sys.exit(1)
