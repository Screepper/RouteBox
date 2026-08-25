import aiohttp
from langchain_core.tools import tool

from config import ROUTEBOX_BASE_URL
from common.remote_call_r import R, DetectResultLog
from common.agent_logger import logger


async def _get_latest_detection_record():
    """pull latest detection record"""
    logger.info("Fetching latest detection record...")

    try:
        async with aiohttp.ClientSession() as session:
            url = f"{ROUTEBOX_BASE_URL}/detect/loadLatestRecord"
            async with session.get(url) as res:
                r: R = await res.json()
                if r["code"] == 200:
                    normal_return = r["data"]
                    if not normal_return:
                        logger.info(f"Got detection record but its empty")
                        return {"isAnomaly": False}
                    logger.info(f"Got 1 latest detection record")
                    return normal_return
                else:
                    error_text = r["mes"] or "Unknown error"
                    logger.info(f"Failed to get detection record: {error_text}")
                    return {"isAnomaly": False}
    except Exception as e:
        logger.error(f"Error fetching detection record: {e}")
        return {"isAnomaly": False}


@tool
async def get_latest_detection_record():
    """pull latest detection record"""
    return await _get_latest_detection_record()


async def _update_latest_detect_record(anomaly_data: DetectResultLog):
    """save anomaly analysis record to database"""
    logger.info(f"Saving anomaly record...")

    try:
        async with aiohttp.ClientSession() as session:
            url = f"{ROUTEBOX_BASE_URL}/detect/updateLatestDetectRecord"
            async with session.post(url, json=anomaly_data) as res:
                r: R = await res.json()
                if r["code"] == 200:
                    logger.info(f"Anomaly record saved successfully")
                    return {"success": True}
                else:
                    error_text = r["mes"] or "Unknown error"
                    logger.info(f"Failed to save anomaly record: {error_text}")
                    return {"success": False, "error": error_text}
    except Exception as e:
        logger.error(f"Error saving anomaly record: {e}")
        return {"success": False, "error": str(e)}
