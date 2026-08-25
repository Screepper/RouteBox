import aiohttp
from langchain_core.tools import tool

from config import ROUTEBOX_BASE_URL
from common.remote_call_r import R, ForceRevokeInfo
from common.agent_logger import logger


async def _force_revoke() -> ForceRevokeInfo:
    """force revoke attack"""
    logger.info(f"Force revoking attack")

    try:
        async with aiohttp.ClientSession() as session:
            url = f"{ROUTEBOX_BASE_URL}/attack/forceRevoke"
            async with session.get(url) as res:
                r: R = await res.json()
                if r["code"] == 200:
                    logger.info(f"Attack force revoked")
                    return {"success": True, **r["data"]}
                else:
                    error_text = r["mes"] or "Unknown error"
                    logger.info(f"Failed to force revoke attack: {error_text}")
                    return {"success": False, "error": error_text}
    except Exception as e:
        logger.error(f"Error revoking attack: {e}")
        return {"success": False, "error": str(e)}


@tool
async def force_revoke():
    """force revoke attack"""
    return await _force_revoke()
