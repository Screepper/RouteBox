import inspect

from asyncio import Queue
from typing import Dict
from datetime import datetime

from common.agent_logger import logger


class MesQueue:
    _queue: Queue = Queue()
    _subscribers: set = set()

    @classmethod
    async def put(
        cls, content, role: str = "rca_agent", mes_type: str = "answer"
    ) -> None:
        """put message to queue"""
        logger.info(f"{role}: {content}")
        await cls._queue.put(
            {
                "type": mes_type,
                "role": role,
                "content": content,
                "timestamp": datetime.now().isoformat(),
            }
        )

    @classmethod
    async def put_extra(
        cls,
        content,
        role: str = "rca_agent",
        extra: Dict = {},
        mes_type: str = "answer",
    ) -> None:
        """put message with extra info to queue"""
        logger.info(f"{role} (extra): {content}")
        await cls._queue.put(
            {
                "type": mes_type,
                "role": role,
                "content": content,
                "extra": extra,
                "timestamp": datetime.now().isoformat(),
            }
        )

    @classmethod
    async def get(cls) -> Dict:
        """get message from queue (blocking)"""
        message = await cls._queue.get()
        return message

    @classmethod
    def subscribe(cls, callback) -> None:
        """subscribe message queue"""
        cls._subscribers.add(callback)

    @classmethod
    def unsubscribe(cls, callback) -> None:
        """unsubscribe message queue"""
        if callback in cls._subscribers:
            cls._subscribers.remove(callback)

    @classmethod
    async def broadcast(cls, message: Dict) -> None:
        """broadcast message to all subscribers"""
        for callback in cls._subscribers:
            if inspect.iscoroutinefunction(callback):
                await callback(message)
            else:
                callback(message)
