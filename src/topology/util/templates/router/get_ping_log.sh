#!/bin/bash

LOG_FOLDER="/usr/routebox/logs/realtime"
LOG_FILE="$LOG_FOLDER/ping.log"
MAX_BATCH_SIZE=50 # max batch size
BATCH_PERCENT=25  # max batch size percent
INTERVAL=10       # base intervals
my_ip=""
target_ips=()            # all target IPs (exclude node self)
shuffled_ips=()          # shuffled IPs
current_index=0          # current batch start index
batch_size=0             # actual batch size

declare -a batch_targets # current batch target IPS

# all_ips

# initialize self-ip and target IPs
init_ips() {
    local_ips=$(ip -4 -o addr show up | awk '!/lo:/ && !/127\./ {split($4, a, "/"); print a[1]}' | sort -u)
    for local_ip in $local_ips; do
        for i in "${!all_ips[@]}"; do
            if [[ "${all_ips[$i]}" == "$local_ip" ]]; then
                my_ip="$local_ip"
                target_ips=("${all_ips[@]:0:$i}" "${all_ips[@]:$((i + 1))}") # exclude node self
                return 0
            fi
        done
    done
    echo "[Error]: The local IP was not matched in all_ips" >&2
    return 1
}

# initialize batch info
init_batch() {
    total_nodes=${#target_ips[@]}
    batch_size=$((total_nodes * BATCH_PERCENT / 100)) # calculate batch size
    [ $batch_size -gt $MAX_BATCH_SIZE ] && batch_size=$MAX_BATCH_SIZE
    [ $batch_size -lt 1 ] && batch_size=1 # at least 1 target
}

# shuffle ips
shuffle_ips() {
    shuffled_ips=($(printf "%s\n" "${target_ips[@]}" | shuf))
}

# get current batch targets
get_batch_targets() {
    batch_targets=()
    local count=0
    local index=$current_index
    while [ $count -lt $batch_size ]; do
        batch_targets+=("${shuffled_ips[$index]}")
        index=$(((index + 1) % ${#shuffled_ips[@]}))
        count=$((count + 1))
        [ $index -eq $current_index ] && break
    done
    current_index=$index
}

# log
log() {
    echo "$(date +%s)|$1" >>"$LOG_FILE"
}

# init
if ! init_ips; then
    echo "Failed to initialize IPs. Exit..." >&2
    exit 1
fi
init_batch
shuffle_ips
# keep running
while true; do
    start_time=$(date +%s)
    get_batch_targets # get current batch targets
    log "!BatchTargets|$(echo ${batch_targets[@]} | tr ' ' ',')"
    # ping each target
    for ip in "${batch_targets[@]}"; do
        result=$(ping -c 1 -W 10 -I "$my_ip" "$ip" 2>&1) # timeout 10s
        if echo "$result" | grep -q "time="; then
            delay=$(echo "$result" | awk -F"time=" '/time=/ {print $2}' | awk '{print $1}') # get delay
            log "$ip|$delay"
        else
            log "$ip|10000"
        fi
    done
    elapsed=$(($(date +%s) - start_time))
    wait_time=$((INTERVAL - elapsed))
    if [ $wait_time -gt 0 ]; then # wait for next batch
        sleep $wait_time
    fi
done