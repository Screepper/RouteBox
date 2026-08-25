import asyncio

from config import WS_PORT
from graph.workflow import compile_workflow
from ws.server import WebSocketServer

workflow_config = {"configurable": {"thread_id": "operator-agent-main"}}


class OperatorAgent:

    def __init__(self):
        self.workflow = None
        self.ws_server = None

    async def init(self):
        self.workflow = compile_workflow()
        self.ws_server = WebSocketServer(self.workflow, workflow_config)

        await self.ws_server.start(port=WS_PORT)

        async for _ in self.workflow.astream(
            input={
                "current_operation_start_time": None,
                "anomaly_start_time": None,
                "repair_attempt_count": 0,
                "short_term_memory": [],
                "interrupted": False,
                "interrupt_start_time": None,
                "breakpoint_state": None,
                "user_input": None,
                "chat_history": [],
                "next_path": "monitoring",
                "last_detect_record": None,
            },
            config=workflow_config,
        ):
            pass

    async def stop(self):
        """stop the agent"""
        if self.ws_server:
            await self.ws_server.stop()


async def main():
    agent = OperatorAgent()

    try:
        await agent.init()
    except Exception as e:
        raise e
    finally:
        await agent.stop()


if __name__ == "__main__":
    asyncio.run(main())
