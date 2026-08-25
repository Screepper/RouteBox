from typing import Dict, List
from datetime import datetime


def save_short_term_memory(memory: List[Dict], content: Dict):
    """update short term memory in state"""
    content.update({"timestamp": datetime.now().isoformat()})

    memory.append(content)
    if len(memory) > 50:
        memory = memory[-50:]

    return memory


