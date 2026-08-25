import aiohttp
from typing import Dict, List
from langchain_core.tools import tool

from config import ROUTEBOX_BASE_URL
from common.remote_call_r import R
from common.agent_logger import logger

attack_urls = {
    "BGP-like": f"{ROUTEBOX_BASE_URL}/attack/bgp",
    "Node-like": f"{ROUTEBOX_BASE_URL}/attack/node",
    "Link-like": f"{ROUTEBOX_BASE_URL}/attack/link",
}


async def _get_attackable_info():
    """load attackable attack types, nodes, links and other info"""
    logger.info("Fetching attackable nodes and attack types...")

    try:
        async with aiohttp.ClientSession() as session:
            url = f"{ROUTEBOX_BASE_URL}/attack/loadAttackableInfo"
            async with session.post(url) as res:
                r: R = await res.json()
                if r["code"] == 200:
                    logger.info(f"Got attackable info")
                    return r["data"]
                else:
                    error_text = r["mes"] or "Unknown error"
                    logger.info(f"Failed to get attackable info: {error_text}")
                    return {"success": False, "error": error_text}
    except Exception as e:
        logger.error(f"Error fetching attackable info: {e}")
        return {"success": False, "error": str(e)}


@tool
async def get_attackable_info():
    """load attackable attack types, nodes, links and other info"""
    return await _get_attackable_info()


@tool
async def launch_attack(
    attack_type: str,
    belong_to: str,
    serial_number: int,
    attacker: int | None,
    victims: List[int | str],
):
    """
    launch attack and return attack event id (if success)

    Args:
        attack_type (str): e.g., "Prefix Hijacking", "Node Down"
        belong_to (str): Literal["BGP-like", "Node-like", "Link-like"]
        serial_number (int): serial number of the attack type
        attacker (int | None): attacker node id (if the attack type requires attacker)
        victims (List[int | str]): victim id list (nodeIds or linkIds, but not both)
    """
    logger.info(
        f"Launching attack: type={attack_type}, belong_to={belong_to}, serial_number={serial_number}, attacker={attacker}, victims={victims}"
    )

    # Determine the attack url based on attack type
    attack_url = None
    for key, url in attack_urls.items():
        if key == belong_to:
            attack_url = f"{url}/{serial_number}"
            break

    if not attack_url:
        logger.info(f"Unsupported attack params")
        return {"success": False, "error": f"Unsupported attack params"}

    # Prepare payload based on attack type
    payload: Dict = {"targetIds": victims}
    if belong_to == "BGP-like":
        payload["attackerId"] = attacker

    try:
        async with aiohttp.ClientSession() as session:
            async with session.post(url=attack_url, json=payload) as res:
                r: R = await res.json()
                if r["code"] == 200:
                    attack_id = r["data"]
                    logger.info(f"Attack launched successfully: {attack_id}")
                    return {"success": True, "attack_id": attack_id}
                else:
                    error_text = r["mes"] or "Unknown error"
                    logger.info(f"Failed to launch {attack_type} : {error_text}")
                    return {"success": False, "error": error_text}
    except Exception as e:
        logger.error(f"Error launching attack: {e}")
        return {"success": False, "error": str(e)}


async def _check_attackable() -> bool:
    """check if attackable"""
    logger.info(f"Checking attackable...")

    try:
        async with aiohttp.ClientSession() as session:
            url = f"{ROUTEBOX_BASE_URL}/attack/checkAttackable"
            async with session.get(url) as res:
                r: R = await res.json()
                if r["code"] == 200:
                    curr_sub_earliest_normal: int = r["data"]
                    if curr_sub_earliest_normal > 0:
                        # now is after the earliest normal time, attackable
                        attackable = True
                        logger.info(f"Attackable now!")
                    else:
                        attackable = False
                        logger.info(
                            f"Not attackable now, still wait at least {-curr_sub_earliest_normal / 60:.2f} minutes"
                        )
                    return attackable
                else:
                    error_text = r["mes"] or "Unknown error"
                    logger.info(f"Failed to check attackable : {error_text}")
                    return False
    except Exception as e:
        logger.error(f"Error checking attackable: {e}")
        return False


async def check_attackable() -> bool:
    """check if attackable"""
    return await _check_attackable()


@tool
async def recommend_targets(
    attack_category: int, attacker_id: int = None
) -> Dict:
    """
    Fetch recommended attack targets.

    Args:
        attack_category (int): Attack category, 0 for BGP-like, 1 for Node-like, 2 for Link-like
        attacker_id (int, optional): Attacker node id (only required for BGP-like attacks)

    Returns:
        List of recommended targets with nodeId/linkId, score, and expectedImpactRatio
    """
    logger.info(
        f"Fetching recommended targets: category={attack_category}, attacker_id={attacker_id}"
    )
    url = f"{ROUTEBOX_BASE_URL}/topology/recommendTargets"

    try:
        async with aiohttp.ClientSession() as session:
            if attack_category == 0 and attacker_id is None:
                return {
                    "success": False,
                    "error": "attacker_id is required for BGP-like attacks",
                }

            payload: Dict = {
                "attackCategory": attack_category,
                "attackerId": attacker_id,
            }
            async with session.post(url, json=payload) as res:
                r: R = await res.json()
                if r["code"] == 200:
                    targets = r["data"]
                    logger.info(f"Received {len(targets)} recommended targets")
                    return {"success": True, "targets": targets}
                else:
                    error_text = r["mes"] or "Unknown error"
                    logger.info(f"Failed to fetch recommendations: {error_text}")
                    return {"success": False, "error": error_text}
    except Exception as e:
        logger.error(f"Error fetching recommendations: {e}")
        return {"success": False, "error": str(e)}
