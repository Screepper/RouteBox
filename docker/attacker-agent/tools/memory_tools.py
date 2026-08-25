import os
from typing import Dict, List
from datetime import datetime

from langchain_core.tools import tool

from config import EXPERIENCE_PATH, STORAGE_DIR


def save_short_term_memory(memory: List[Dict], content: Dict):
    """update short term memory in state"""
    content.update({"timestamp": datetime.now().isoformat()})

    memory.append(content)
    if len(memory) > 50:
        memory = memory[-50:]

    return memory


def _load_experience() -> str:
    """load existing experience markdown file, return empty string if not exists"""
    if not os.path.exists(EXPERIENCE_PATH):
        return ""
    try:
        with open(EXPERIENCE_PATH, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return ""


def _save_experience(content: str):
    """save experience markdown file"""
    os.makedirs(STORAGE_DIR, exist_ok=True)
    with open(EXPERIENCE_PATH, "w", encoding="utf-8") as f:
        f.write(content)


@tool
def get_experience():
    """load attack experience from local markdown file"""
    return _load_experience()
