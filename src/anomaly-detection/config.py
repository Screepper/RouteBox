# Service Config
SERVICE_PORT = 12101

# Detection Config (Default window time range is 60 * 30 = 1800s (30min))
DETECT_WINDOW_SIZE = 60  # detection window size (60 points)
COMBINED_SECONDS = 30  # point combine seconds (30s)
REAL_TIME_MODE = True  # if True, this will ignore the historical anomalies that over the MAX_PREV_ANOMALY_GAP defined in the algorithm_util.py
