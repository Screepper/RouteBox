import json

from asyncio import create_task, sleep, CancelledError
from aiohttp import web, WSMsgType
from typing import Set
from .queue_util import MesQueue


class WebSocketServer:
    """WebSocket server, used to exchange information with the frontend"""

    def __init__(self, workflow=None, workflow_config=None):
        """initialize WebSocket server"""
        self.workflow = workflow
        self.workflow_config = workflow_config

        self.app = web.Application()
        self.websockets: Set[web.WebSocketResponse] = set()
        self._runner = None
        self._task = None
        self.app.router.add_get("/ws", self._handle_websocket)

    async def _handle_websocket(self, request: web.Request) -> web.WebSocketResponse:
        """handle WebSocket connection"""
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        self.websockets.add(ws)
        async for mes in ws:
            if mes.type == WSMsgType.TEXT:
                try:
                    await self._handle_client_message(json.loads(mes.data))
                except json.JSONDecodeError:
                    continue
            else:
                continue
        self.websockets.discard(ws)

        return ws

    async def _handle_client_message(self, data: dict):
        """handle message from client"""
        content = data.get("content", "")
        try:
            if self.workflow:
                self.workflow.update_state(
                    self.workflow_config, {"interrupted": True, "user_input": content}
                )
        except Exception:
            await MesQueue.put("Failed to chat!")
            pass

    async def broadcast(self, message: dict):
        """broadcast message to all connected clients"""
        if not self.websockets:
            return

        message_str = json.dumps(message)

        disconnected = set()
        for ws in self.websockets:
            try:
                await ws.send_str(message_str)
            except Exception:
                disconnected.add(ws)

        for ws in disconnected:
            self.websockets.discard(ws)

    async def _message_pusher(self):
        """daemon task: continuously pull messages from queue and push to clients"""
        while True:
            try:
                message = await MesQueue.get()
                await self.broadcast(message)
            except Exception:
                await sleep(1)

    async def start(self, port: int = 4399):
        """start WebSocket server"""
        self._runner = web.AppRunner(self.app)
        await self._runner.setup()

        site = web.TCPSite(self._runner, "0.0.0.0", port)
        await site.start()

        self._task = create_task(self._message_pusher())

    async def stop(self):
        """stop WebSocket server"""
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except CancelledError:
                pass

        for ws in self.websockets:
            await ws.close()

        if self._runner:
            await self._runner.cleanup()
