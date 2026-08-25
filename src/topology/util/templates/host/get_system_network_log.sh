#!/bin/bash

LOG_FOLDER="/usr/routebox/logs/realtime"
LOG_FILE="${LOG_FOLDER}/system_network.log"
mkdir -p "$LOG_FOLDER"
# get cgroup path in container
SELF_CGROUP=""
while IFS= read -r line; do
    [[ "$line" =~ ^# ]] && continue
    IFS=':' read -r _ _ path <<<"$line"
    SELF_CGROUP="$path"
done </proc/self/cgroup
[[ "$SELF_CGROUP" != /* ]] && SELF_CGROUP="/$SELF_CGROUP"
CGROUP_PATH="/sys/fs/cgroup${SELF_CGROUP}"
# read host total memory
HOST_TOTAL_KB=0
if read -r line </proc/meminfo 2>/dev/null && [[ "$line" =~ MemTotal:[[:space:]]*([0-9]+) ]]; then
    HOST_TOTAL_KB=${BASH_REMATCH[1]}
fi
HOST_TOTAL_MEM=$((HOST_TOTAL_KB * 1024))
# init nics cache (exclude lo)
declare -A PREV_RX_BYTES PREV_TX_BYTES PREV_RX_PACKETS PREV_TX_PACKETS
INTERFACES=()
for nic in /sys/class/net/*; do
    nic_name=${nic##*/}
    [[ "$nic_name" == "lo" || ! -d "$nic" ]] && continue
    INTERFACES+=("$nic_name")
    # read current nic value
    read -r rx <"$nic/statistics/rx_bytes" 2>/dev/null || rx=0
    read -r tx <"$nic/statistics/tx_bytes" 2>/dev/null || tx=0
    read -r rxp <"$nic/statistics/rx_packets" 2>/dev/null || rxp=0
    read -r txp <"$nic/statistics/tx_packets" 2>/dev/null || txp=0
    PREV_RX_BYTES["$nic_name"]=$rx
    PREV_TX_BYTES["$nic_name"]=$tx
    PREV_RX_PACKETS["$nic_name"]=$rxp
    PREV_TX_PACKETS["$nic_name"]=$txp
done

# init cpu cache
PREV_USER_USE=0
PREV_SYSTEM_USE=0
[[ -f "$CGROUP_PATH/cpu.stat" ]] && while IFS= read -r line; do
    [[ "$line" == user_usec* ]] && PREV_USER_USE=${line##* }
    [[ "$line" == system_usec* ]] && PREV_SYSTEM_USE=${line##* }
done <"$CGROUP_PATH/cpu.stat" 2>/dev/null
# init io cache
declare -A PREV_WBYTES PREV_RIOS PREV_WIOS
[[ -f "$CGROUP_PATH/io.stat" ]] && while IFS= read -r line; do
    dev=${line%% *}
    wbytes=${line#*wbytes=}
    wbytes=${wbytes%% *}
    rios=${line#*rios=}
    rios=${rios%% *}
    wios=${line#*wios=}
    wios=${wios%% *}
    PREV_WBYTES["$dev"]=${wbytes:-0}
    PREV_RIOS["$dev"]=${rios:-0}
    PREV_WIOS["$dev"]=${wios:-0}
done <"$CGROUP_PATH/io.stat" 2>/dev/null

# loop
while true; do
    start=$(date +%s)
    # ============== cpu metrics ==============
    current_user_use=0
    current_system_use=0
    [[ -f "$CGROUP_PATH/cpu.stat" ]] && while IFS= read -r line; do
        [[ "$line" == user_usec* ]] && current_user_use=${line##* }
        [[ "$line" == system_usec* ]] && current_system_use=${line##* }
    done <"$CGROUP_PATH/cpu.stat" 2>/dev/null
    # calculate delta
    user_delta=$((current_user_use - PREV_USER_USE))
    system_delta=$((current_system_use - PREV_SYSTEM_USE))
    # calculate ratio
    cpu_use_ratio=$(bc -l <<<"scale=6; $user_delta / 10000")
    system_use_ratio=$(bc -l <<<"scale=6; $system_delta / 10000")
    PREV_USER_USE=$current_user_use
    PREV_SYSTEM_USE=$current_system_use
    cpu_data="${cpu_use_ratio}|${system_use_ratio}"
    # ============== memory metrics ==============
    memory_current=0
    memory_max="max"
    [[ -f "$CGROUP_PATH/memory.current" ]] && read -r memory_current <"$CGROUP_PATH/memory.current" 2>/dev/null
    [[ -f "$CGROUP_PATH/memory.max" ]] && read -r memory_max <"$CGROUP_PATH/memory.max" 2>/dev/null
    # process memory_max: use host total memory when no limit
    if [[ "$memory_max" == "max" || -z "$memory_max" || ! "$memory_max" =~ ^[0-9]+$ ]]; then
        memory_max=$HOST_TOTAL_MEM
    fi
    used_memory_kb=$((memory_current / 1024))
    memory_use_ratio=0
    ((memory_max > 0)) && memory_use_ratio=$(bc -l <<<"scale=6; $memory_current / $memory_max")
    mem_data="$used_memory_kb|$memory_use_ratio"
    # ============== io metrics ==============
    tps=0
    io_write_kb=0
    declare -A CURRENT_RBYTES CURRENT_WBYTES CURRENT_RIOS CURRENT_WIOS
    [[ -f "$CGROUP_PATH/io.stat" ]] && while IFS= read -r line; do
        dev=${line%% *}
        wbytes=${line#*wbytes=}
        wbytes=${wbytes%% *}
        rios=${line#*rios=}
        rios=${rios%% *}
        wios=${line#*wios=}
        wios=${wios%% *}
        # calculate delta
        wbytes_delta=$((wbytes - PREV_WBYTES["$dev"]))
        rios_delta=$((rios - PREV_RIOS["$dev"]))
        wios_delta=$((wios - PREV_WIOS["$dev"]))
        # accumulate total
        tps=$((tps + rios_delta + wios_delta))
        io_write_kb=$(bc -l <<<"scale=6; $io_write_kb + $wbytes_delta / 1024")
        # update cache
        PREV_WBYTES["$dev"]=$wbytes
        PREV_RIOS["$dev"]=$rios
        PREV_WIOS["$dev"]=$wios
    done <"$CGROUP_PATH/io.stat" 2>/dev/null
    io_data="$tps|$io_write_kb"
    # ============== network metrics ==============
    nic_data=""
    for nic in "${INTERFACES[@]}"; do
        # read current nic value
        read -r rx <"/sys/class/net/$nic/statistics/rx_bytes" 2>/dev/null || rx=0
        read -r tx <"/sys/class/net/$nic/statistics/tx_bytes" 2>/dev/null || tx=0
        read -r rxp <"/sys/class/net/$nic/statistics/rx_packets" 2>/dev/null || rxp=0
        read -r txp <"/sys/class/net/$nic/statistics/tx_packets" 2>/dev/null || txp=0
        # calculate delta
        rx_delta=$((rx - PREV_RX_BYTES["$nic"]))
        tx_delta=$((tx - PREV_TX_BYTES["$nic"]))
        rxp_delta=$((rxp - PREV_RX_PACKETS["$nic"]))
        txp_delta=$((txp - PREV_TX_PACKETS["$nic"]))
        # convert to KB
        rx_kb=$(bc -l <<<"scale=6; $rx_delta / 1024")
        tx_kb=$(bc -l <<<"scale=6; $tx_delta / 1024")
        # update cache
        PREV_RX_BYTES["$nic"]=$rx
        PREV_TX_BYTES["$nic"]=$tx
        PREV_RX_PACKETS["$nic"]=$rxp
        PREV_TX_PACKETS["$nic"]=$txp
        nic_data+="|${nic}-${rxp_delta}-${txp_delta}-${rx_kb}-${tx_kb}"
    done
    # to log
    echo "${start}|${cpu_data}|${mem_data}|${io_data}${nic_data}" >>"${LOG_FILE}"
    # sleep if need
    if (($(($(date +%s) - start)) == 0)); then
        sleep 1
    fi
done