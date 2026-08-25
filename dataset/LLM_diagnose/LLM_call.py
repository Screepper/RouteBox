import json
import os
import time

import requests
from dotenv import load_dotenv

load_dotenv()
base_url = os.getenv("BASE_URL")
api_key = os.getenv("API_KEY")
api_model = os.getenv("API_MODEL")

RETRY_TIMES = 3
RETRY_DELAY = 1


def call(
    query: str, enable_thinking: bool = True, output: bool = True, stream: bool = False
):
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
    }
    payload = {
        "model": api_model,
        "messages": [{"role": "user", "content": query}],
        "enable_thinking": enable_thinking,
    }
    answers = ""

    retry = 0
    while retry <= RETRY_TIMES:
        try:
            if stream:
                payload["stream"] = True
                is_reasoning = True

                with requests.post(
                    url=f"{base_url}/chat/completions",
                    headers=headers,
                    json=payload,
                    stream=True,
                    timeout=600,
                ) as response:
                    if output:
                        print("=" * 40 + "Reasoning" + "=" * 40)

                    for line in response.iter_lines():
                        if not line:
                            continue
                        line_str = line.decode("utf-8").strip()[6:]  # remove "data: "
                        if line_str == "[DONE]":
                            break  # stream end

                        # parse stream chunk
                        chunk = json.loads(line_str)
                        if not chunk.get("choices", []):
                            continue
                        delta = chunk["choices"][0].get("delta", {})
                        reason = delta.get("reasoning_content", "")  # reasoning content
                        answer = delta.get("content", "")  # answer content

                        if reason and output:
                            print(reason, end="", flush=True)
                        if answer:
                            if is_reasoning and output:
                                is_reasoning = False
                                print("\n" + "=" * 40 + "Answering" + "=" * 40)
                            answers += answer
                            if output:
                                print(answer, end="", flush=True)
                    print("\n")
                    break
            else:
                response = requests.post(
                    url=f"{base_url}/chat/completions",
                    headers=headers,
                    json=payload,
                    timeout=600,
                )
                answers = response.json()["choices"][0]["message"]["content"]
                if output:
                    print(f"{answers}\n")
                break

        except Exception as e:
            retry += 1
            time.sleep(RETRY_DELAY)
            if retry <= RETRY_TIMES:
                print(f"{e}\nRequest failed, retry {retry}/{RETRY_TIMES}")

            continue

    if retry > RETRY_TIMES:
        print("Request failed after retry times ", RETRY_TIMES)

    return answers
