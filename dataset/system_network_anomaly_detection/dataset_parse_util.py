import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class NicInfo:
    name: str = ""
    receivePacketsRate: int = 0
    transmitPacketsRate: int = 0
    receiveKbRate: float = 0.0
    transmitKbRate: float = 0.0


@dataclass
class SystemNetworkLog:
    logId: Optional[str] = ""
    eventId: Optional[str] = ""
    networkId: Optional[str] = 0
    nodeId: int = -1
    timestamp: int = 0
    cpuUseRatio: float = 0.0
    systemUseRatio: float = 0.0
    usedMemoryKb: int = 0
    memoryUseRatio: float = 0.0
    tps: int = 0
    ioReadKb: float = 0.0
    ioWriteKb: float = 0.0
    nics: List[NicInfo] = field(default_factory=list)
    totalReceivePacketsRate: int = 0
    totalTransmitPacketsRate: int = 0
    totalReceiveKbRate: float = 0.0
    totalTransmitKbRate: float = 0.0


LOG_FIELDS = [
    ("nodeId", int, -1),
    ("timestamp", int, 0),
    ("cpuUseRatio", float, 0.0),
    ("systemUseRatio", float, 0.0),
    ("usedMemoryKb", int, 0),
    ("memoryUseRatio", float, 0.0),
    ("tps", int, 0),
    ("ioWriteKb", float, 0.0),
]
NIC_FIELDS = [
    ("name", str, ""),
    ("receivePacketsRate", int, 0),
    ("transmitPacketsRate", int, 0),
    ("receiveKbRate", float, 0.0),
    ("transmitKbRate", float, 0.0),
]
REMAIN_FIELDS = [
    ("totalReceivePacketsRate", int, 0),
    ("totalTransmitPacketsRate", int, 0),
    ("totalReceiveKbRate", float, 0.0),
    ("totalTransmitKbRate", float, 0.0),
    ("label", int, 0),
]


def get_field_names(log: SystemNetworkLog) -> List[str]:
    """Get all detectable field names"""
    field_names = [
        "cpuUseRatio",
        "systemUseRatio",
        "usedMemoryKb",
        "memoryUseRatio",
        "tps",
        "ioWriteKb",
    ]

    for nic in log.nics:
        nic_prefix = f"nics[{nic.name}]."
        field_names.extend(
            [
                f"{nic_prefix}receivePacketsRate",
                f"{nic_prefix}transmitPacketsRate",
                f"{nic_prefix}receiveKbRate",
                f"{nic_prefix}transmitKbRate",
            ]
        )

    if len(log.nics) > 1:
        field_names.extend(
            [
                "totalReceivePacketsRate",
                "totalTransmitPacketsRate",
                "totalReceiveKbRate",
                "totalTransmitKbRate",
            ]
        )

    return field_names


def extract_field_value(log: SystemNetworkLog, field_name: str) -> Optional[float]:
    """Get the field value"""
    if field_name == "timestamp":
        return log.timestamp
    if field_name == "cpuUseRatio":
        return log.cpuUseRatio
    elif field_name == "systemUseRatio":
        return log.systemUseRatio
    elif field_name == "usedMemoryKb":
        return log.usedMemoryKb
    elif field_name == "memoryUseRatio":
        return log.memoryUseRatio
    elif field_name == "tps":
        return float(log.tps)
    elif field_name == "ioWriteKb":
        return log.ioWriteKb
    elif field_name == "totalReceivePacketsRate":
        return float(log.totalReceivePacketsRate)
    elif field_name == "totalTransmitPacketsRate":
        return float(log.totalTransmitPacketsRate)
    elif field_name == "totalReceiveKbRate":
        return log.totalReceiveKbRate
    elif field_name == "totalTransmitKbRate":
        return log.totalTransmitKbRate
    if field_name.startswith("nics["):
        parts = field_name.split("].")

        if len(parts) < 2:
            return None

        nic_name = parts[0][5:]
        nic_field = parts[1]

        for nic in log.nics:
            if nic.name == nic_name:
                if nic_field == "receivePacketsRate":
                    return float(nic.receivePacketsRate)
                elif nic_field == "transmitPacketsRate":
                    return float(nic.transmitPacketsRate)
                elif nic_field == "receiveKbRate":
                    return nic.receiveKbRate
                elif nic_field == "transmitKbRate":
                    return nic.transmitKbRate


def extract_field_values(logs: List[SystemNetworkLog], field: str) -> List[float]:
    """Get one field's value list from all the logs"""
    values = []

    for log in logs:
        val = extract_field_value(log, field)
        if val is not None:
            values.append(val)

    return values


def row_data_to_log(line: str) -> Optional[SystemNetworkLog]:
    try:
        parts = line.split(
            ",",
            len(LOG_FIELDS) + 2 + len(REMAIN_FIELDS) - 1,
        )
        log = SystemNetworkLog()
        idx = 0
        # base fields
        for field, typ, default in LOG_FIELDS:
            if idx < len(parts) and parts[idx].strip():
                setattr(log, field, typ(parts[idx].strip()))
            else:
                setattr(log, field, default)
            idx += 1

        # nic fields
        nic_count = (
            int(parts[idx].strip()) if idx < len(parts) and parts[idx].strip() else 0
        )
        idx += 1
        nics = []
        if nic_count > 0 and idx < len(parts) and parts[idx].strip():
            for nic_str in parts[idx].strip().split("|"):
                nic_fields = nic_str.split(";")
                nic = NicInfo()
                for i, (field, typ, default) in enumerate(NIC_FIELDS):
                    if i < len(nic_fields) and nic_fields[i].strip():
                        setattr(nic, field, typ(nic_fields[i].strip()))
                    else:
                        setattr(nic, field, default)
                nics.append(nic)
        log.nics = nics
        idx += 1
        # remaining fields
        for field, typ, default in REMAIN_FIELDS:
            if idx < len(parts) and parts[idx].strip():
                setattr(log, field, typ(parts[idx].strip()))
            else:
                setattr(log, field, default)
            idx += 1
        return log
    except Exception as e:
        print(f"Error parsing log: {e}")
        return None


def get_dimension(nodeId: int) -> int:
    """get the dimension of the csv file of specific nodeId"""
    csv_path = f"../train/csv/{nodeId}.csv"
    with open(csv_path, "r") as r:
        first_log = row_data_to_log(r.readline().strip())
        dim = len(get_field_names(first_log))
        return dim


def generate_data(train_path, eval_path):
    import torch
    # Load csv files
    with open(train_path, "r") as r:
        train_logs = [row_data_to_log(row) for row in r]
    with open(eval_path, "r") as r:
        eval_logs = [row_data_to_log(row) for row in r]

    # Extract labels
    train_labels = extract_field_values(train_logs, "label")
    eval_labels = extract_field_values(eval_logs, "label")

    # Extract features
    fields = get_field_names(train_logs[0])
    train_features = np.array(
        [[extract_field_value(log, _field) for _field in fields] for log in train_logs]
    )
    eval_features = np.array(
        [[extract_field_value(log, _field) for _field in fields] for log in eval_logs]
    )

    # Standardize features
    scaler = StandardScaler()
    scaler.fit(train_features)
    train_norm = scaler.transform(train_features)
    eval_norm = scaler.transform(eval_features)

    # Split train set and validation set
    X_train, X_val, y_train, y_val = train_test_split(
        train_norm, train_labels, test_size=0.2, random_state=42, stratify=train_labels
    )

    # Convert to torch tensor
    dtype = torch.float32
    X_train = torch.tensor(X_train, dtype=dtype)
    X_val = torch.tensor(X_val, dtype=dtype)
    X_eval = torch.tensor(eval_norm, dtype=dtype)
    y_train = torch.tensor(y_train, dtype=dtype)

    y_val = torch.tensor(y_val, dtype=dtype)
    y_eval = torch.tensor(eval_labels, dtype=dtype)

    return X_train, X_val, y_train, y_val, X_eval, y_eval
