FROM python:3.12-slim

WORKDIR /app

RUN apt update && apt install -y curl && rm -rf /var/lib/apt/lists/*
RUN curl -LsSf https://astral.sh/uv/install.sh | sh

ENV PATH="/root/.local/bin:${PATH}"

COPY . /app/

RUN uv python pin 3.12 && uv venv && uv sync

EXPOSE 4399

CMD ["/app/.venv/bin/python", "main.py"]