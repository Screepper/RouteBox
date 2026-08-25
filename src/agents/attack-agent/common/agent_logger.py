import logging
import os

from config import LOGGER_DIR

LOG_LEVEL = "INFO"
LOG_TO_CONSOLE = True
LOG_TO_FILE = False


def setup_logger() -> logging.Logger:
    """configure and instantiate global logger"""
    my_logger = logging.getLogger("ATTACKER_AGENT_LOGGER")
    if my_logger.handlers:
        return my_logger

    my_logger.setLevel(LOG_LEVEL)
    formatter = logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - [%(filename)s:%(lineno)d] - %(message)s",
                                  "%Y-%m-%d %H:%M:%S")

    # register console handler
    if LOG_TO_CONSOLE:
        console_handler = logging.StreamHandler()
        console_handler.setLevel(LOG_LEVEL)
        console_handler.setFormatter(formatter)
        my_logger.addHandler(console_handler)

    # register file handler
    if LOG_TO_FILE:
        from logging.handlers import TimedRotatingFileHandler
        os.makedirs(LOGGER_DIR, exist_ok=True)

        file_handler = TimedRotatingFileHandler(
            filename=os.path.join(LOGGER_DIR, "agent.log"),
            when="midnight",
            interval=1,
            backupCount=7,
            encoding='utf-8'
        )
        file_handler.setLevel(LOG_LEVEL)
        file_handler.setFormatter(formatter)
        my_logger.addHandler(file_handler)

    return my_logger


# instantiate
logger = setup_logger()
