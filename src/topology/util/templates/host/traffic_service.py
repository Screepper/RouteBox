import aiohttp
import asyncio
import os
import random
import socket
import multiprocessing
from aiohttp.web import Response, Application, AppRunner, TCPSite
from datetime import datetime
from pathlib import Path

SERVER_PORTS = [9001, 9002, 9003, 9004]  # server ports
FILES_FOLDER = "/usr/routebox/files"
FILE_SMALL = f"{FILES_FOLDER}/small.bin"
FILE_MEDIUM = f"{FILES_FOLDER}/medium.bin"
FILE_LARGE = f"{FILES_FOLDER}/large.bin"

DAILY_FACTORS = [
    1.1,
    0.9,
    0.8,
    0.7,
    0.6,
    0.7,
    0.8,
    0.9,
    1.0,
    1.2,
    1.5,
    1.4,
    1.3,
    1.2,
    1.3,
    1.4,
    1.5,
    1.4,
    1.3,
    1.2,
    1.3,
    1.4,
    1.3,
    1.2,
]  # daily factors
WEEKLY_FACTORS = [1.2, 1.3, 1.3, 1.2, 1.1, 0.9, 0.8]  # weekly factors
JITTERS = [-0.03, -0.02, -0.01, 0.0, 0.01, 0.02, 0.03]
RECONNECT_DELAY = 5

# SERVERS, SMALL_SIZE, MEDIUM_SIZE, LARGE_SIZE, BASE_INTERVAL


# get local IPv4 (non-loopback)
def get_local_ip():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
    return local_ip


# check if current node is server
def is_server(ip):
    return ip in SERVERS


# create files if missing
def create_files():
    Path(FILES_FOLDER).mkdir(parents=True, exist_ok=True)
    if not Path(FILE_SMALL).exists():
        with open(FILE_SMALL, "wb") as f:
            f.write(os.urandom(SMALL_SIZE))
    if not Path(FILE_MEDIUM).exists():
        with open(FILE_MEDIUM, "wb") as f:
            f.write(os.urandom(MEDIUM_SIZE))
    if not Path(FILE_LARGE).exists():
        with open(FILE_LARGE, "wb") as f:
            f.write(os.urandom(LARGE_SIZE))


# calculate combined time factor
def get_time_factor():
    now = datetime.now()
    minute = now.minute
    hour = (now.hour + 8) % 24  # Beijing time (UTC+8)
    weekday = now.weekday()  # (0-6)
    # hour smooth transition
    current_hour_factor = DAILY_FACTORS[hour]
    next_hour_factor = DAILY_FACTORS[(hour + 1) % 24]
    hour_progress = minute / 60.0
    daily_factor = (
        current_hour_factor + (next_hour_factor - current_hour_factor) * hour_progress
    )
    # day smooth transition
    day_progress = (hour * 60 + minute) / (24 * 60)
    current_week_factor = WEEKLY_FACTORS[weekday]
    next_week_factor = WEEKLY_FACTORS[(weekday + 1) % 7]
    weekly_factor = (
        current_week_factor + (next_week_factor - current_week_factor) * day_progress
    )
    # final factor
    return daily_factor * weekly_factor


# server: handle long connection requests
async def handle_request(request):
    path = request.path
    if "/large" in path:
        file_path = FILE_LARGE
    elif "/medium" in path:
        file_path = FILE_MEDIUM
    else:
        file_path = FILE_SMALL
    await request.read()
    with open(file_path, "rb") as f:
        file_content = f.read()
    return Response(
        status=200,
        body=file_content,
        headers={
            "Content-Type": "application/octet-stream",
            "Content-Length": str(os.path.getsize(file_path)),
            "Connection": "keep-alive",
        },
    )


# start long connection server
async def run_server():
    create_files()
    app = Application()
    app.router.add_post("/{any:.*}", handle_request)

    runners = []
    for port in SERVER_PORTS:
        runner = AppRunner(app, access_log=None)
        await runner.setup()
        site = TCPSite(runner, "0.0.0.0", port)
        await site.start()
        runners.append(runner)
    try:
        await asyncio.Future()
    finally:
        for runner in runners:
            await runner.cleanup()


# client: send requests to long connection server
class HttpClient:
    def __init__(self):
        create_files()
        self.session = None
        with open(FILE_SMALL, "rb") as f:
            self.small_data = f.read()
        with open(FILE_MEDIUM, "rb") as f:
            self.medium_data = f.read()
        with open(FILE_LARGE, "rb") as f:
            self.large_data = f.read()

    async def task_worker(self, server, port):
        url = f"http://{server}:{port}"
        while True:
            try:
                file_rand = random.randint(0, 99)
                if file_rand < 85:
                    data = self.small_data
                    path = "/small"
                elif file_rand < 98:
                    data = self.medium_data
                    path = "/medium"
                else:
                    data = self.large_data
                    path = "/large"
                time_factor = get_time_factor()
                JITTER_INTERVAL = BASE_INTERVAL + random.choice(JITTERS)
                adjusted_interval = (
                    JITTER_INTERVAL / time_factor
                    if time_factor != 0
                    else JITTER_INTERVAL
                )
                async with self.session.post(url + path, data=data) as resp:
                    await resp.read()
                await asyncio.sleep(adjusted_interval)
            except Exception:
                await asyncio.sleep(RECONNECT_DELAY)

    async def run_process(self, server):
        async with aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(limit=100)
        ) as session:
            self.session = session
            tasks = []
            for port in SERVER_PORTS:
                task = asyncio.create_task(self.task_worker(server, port))
                tasks.append(task)
                await asyncio.sleep(0.2)
            await asyncio.gather(*tasks)


def client_process_entry(server):
    client = HttpClient()
    asyncio.run(client.run_process(server))


def run_clients():
    processes = []
    for server in SERVERS:
        p = multiprocessing.Process(target=client_process_entry, args=(server,))
        p.start()
        processes.append(p)
    for p in processes:
        p.join()


async def main():
    local_ip = get_local_ip()
    if is_server(local_ip):
        await run_server()
    else:
        run_clients()


if __name__ == "__main__":
    asyncio.run(main())
