import asyncio

from config import WS_PORT
from graph.workflow import compile_workflow
from ws.server import WebSocketServer

workflow_config = {"configurable": {"thread_id": "attacker-agent-main"}}


class AttackerAgent:

    def __init__(self):
        self.workflow = None
        self.ws_server = None

    async def start(self):
        """initialize Attacker Agent"""
        self.workflow = compile_workflow()
        self.ws_server = WebSocketServer(self.workflow, workflow_config)

        await self.ws_server.start(port=WS_PORT)

        async for _ in self.workflow.astream(
                input={
                    "expected_next_attack_time": None,
                    "current_attack_start_time": None,
                    "short_term_memory": [],
                    "interrupted": False,
                    "interrupt_start_time": None,
                    "breakpoint_state": None,
                    "user_input": None,
                    "chat_history": [],
                    "next_path": "cooling_check"
                },
                config=workflow_config,
        ):
            pass

    async def stop(self):
        """stop the agent"""
        if self.ws_server:
            await self.ws_server.stop()


async def main():
    agent = AttackerAgent()

    try:
        await agent.start()
    except Exception as e:
        raise e
    finally:
        await agent.stop()


if __name__ == "__main__":
    asyncio.run(main())
