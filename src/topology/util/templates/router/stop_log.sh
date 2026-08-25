#!/bin/bash

PATTERNS=(
    "start_log.sh"
    "get_system_network_log.sh"
    "get_ping_log.sh"
    "get_bgp_log.sh"
)

for pattern in "${PATTERNS[@]}"; do
    pkill -9 -f "$pattern"
done
