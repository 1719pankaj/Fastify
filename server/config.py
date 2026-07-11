import os

OPENF1_BASE_URL = os.getenv("OPENF1_BASE_URL", "https://api.openf1.org/v1")

# Default session to use for simulation (9472 = Bahrain GP 2024)
DEFAULT_SIMULATION_SESSION_KEY = int(os.getenv("DEFAULT_SIMULATION_SESSION_KEY", 9472))

# How often the background worker updates data in live mode (seconds)
LIVE_POLL_INTERVAL = int(os.getenv("LIVE_POLL_INTERVAL", 10))
