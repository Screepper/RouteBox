from typing import List, Optional
from entity.nic_info import NicInfo
from entity.system_network_log import SystemNetworkLog


class DatasetParseUtil:
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
    ]

    @staticmethod
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

    @staticmethod
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

        return None

    @staticmethod
    def extract_field_values(logs: List[SystemNetworkLog], field: str) -> List[float]:
        """Get one field's value list from all the logs"""
        values = []

        for log in logs:
            val = DatasetParseUtil.extract_field_value(log, field)
            if val is not None:
                values.append(val)

        return values

    @staticmethod
    def row_data_to_log(line: str) -> Optional[SystemNetworkLog]:
        try:
            parts = line.split(
                ",",
                len(DatasetParseUtil.LOG_FIELDS)
                + 2
                + len(DatasetParseUtil.REMAIN_FIELDS)
                - 1,
            )
            log = SystemNetworkLog()
            idx = 0

            # Basic fields
            for field, typ, default in DatasetParseUtil.LOG_FIELDS:
                if idx < len(parts) and parts[idx].strip():
                    setattr(log, field, typ(parts[idx].strip()))
                else:
                    setattr(log, field, default)
                idx += 1

            # Nic fields
            nic_count = (
                int(parts[idx].strip())
                if idx < len(parts) and parts[idx].strip()
                else 0
            )
            idx += 1
            nics = []

            if nic_count > 0 and idx < len(parts) and parts[idx].strip():
                for nic_str in parts[idx].strip().split("|"):
                    nic_fields = nic_str.split(";")
                    nic = NicInfo()
                    for i, (field, typ, default) in enumerate(
                        DatasetParseUtil.NIC_FIELDS
                    ):
                        if i < len(nic_fields) and nic_fields[i].strip():
                            setattr(nic, field, typ(nic_fields[i].strip()))
                        else:
                            setattr(nic, field, default)
                    nics.append(nic)
            log.nics = nics
            idx += 1

            # Remaining fields
            for field, typ, default in DatasetParseUtil.REMAIN_FIELDS:
                if idx < len(parts) and parts[idx].strip():
                    setattr(log, field, typ(parts[idx].strip()))
                else:
                    setattr(log, field, default)
                idx += 1

            return log
        except Exception as e:
            print(f"Error parsing log: {e}")
            return None
