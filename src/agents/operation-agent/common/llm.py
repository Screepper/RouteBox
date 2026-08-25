import os

from langchain_openai import ChatOpenAI
from dotenv import load_dotenv

load_dotenv()
API_MODEL = os.getenv("API_MODEL")
API_KEY = os.getenv("API_KEY")
BASE_URL = os.getenv("BASE_URL")


llm = ChatOpenAI(
    model=API_MODEL,
    api_key=API_KEY,
    base_url=BASE_URL,
)

no_reason_llm = ChatOpenAI(
    model=API_MODEL,
    api_key=API_KEY,
    base_url=BASE_URL,
    reasoning_effort="none", # only for GPT series, other may not support and throw error
)
