#!/bin/bash

LOG_ROOT="/usr/routebox/logs"
REALTIME_DIR="$LOG_ROOT/realtime"         # realtime write dir
READY_DIR="$LOG_ROOT/ready"               # last complete batch logs dir
HISTORY_DIR="$LOG_ROOT/history"           # history batch logs dir
MAX_HISTORY=180                           # 1h cache batches
VERSION_FILE="$LOG_ROOT/version_local"    # version file
VERSION_LOCK="$REALTIME_DIR/version.lock" # stop flag
SCRIPTS=(
    "./get_system_network_log.sh"
    "./get_ping_log.sh"
    "./get_bgp_log.sh"
)
BGP_DUMP_DIR="$REALTIME_DIR/bgp_dumps"        # FRR auto-rotated BGP update dump (MRP) dir
MAX_BGP_DUMPS=10                          # Keep only the last 10 BGP dump files
mkdir -p $REALTIME_DIR $READY_DIR $HISTORY_DIR $BGP_DUMP_DIR

# configure BGP command recording and BGP update dump
configure_bgp() {
    chmod 777 /usr/routebox/logs/realtime/bgp_dumps

    vtysh -c "configure" \
          -c "log command" \
          -c "log file /usr/routebox/logs/realtime/router.log informational" \
          -c "dump bgp updates $BGP_DUMP_DIR/bgp_updates_%Y%m%d_%H%M%S.bin 20"
}

# get the latest closed BGP dump file
get_latest_closed_bgp_dump() {
    local second_latest=$(ls -t "$BGP_DUMP_DIR"/bgp_updates_*.bin 2>/dev/null | head -2 | tail -1)
    
    if [ -n "$second_latest" ] && [ -f "$second_latest" ]; then
        echo "$second_latest"
    else
        return 1
    fi
}

# obtain and increment the version number
next_version() {
    if [ ! -f "$VERSION_FILE" ] || [ ! -s "$VERSION_FILE" ]; then
        echo "0" >"$VERSION_FILE"
    fi
    current=$(<"$VERSION_FILE")
    if ! [[ "$current" =~ ^[0-9]+$ ]]; then
        current=0
    fi
    next=$((current + 1))
    echo "$next" >"$VERSION_FILE"
    echo "$next"
}

# check and reload processes
check_processes() {
    cd /usr/routebox/scripts || return 1
    for script in "${SCRIPTS[@]}"; do
        if [ $(pgrep -fc "bash[[:space:]]+$script$") -eq 0 ]; then
            bash "$script" >/dev/null 2>&1 &
        fi
    done
}

configure_bgp
while true; do
    # 1. Process monitoring + version management
    check_processes
    version=$(next_version)
    start_time=$(date +%s)
    sleep 19
    end_time=$(date +%s)
    echo "${version}|${start_time}|${end_time}" >"$VERSION_LOCK"

    # 2. Process REALTIME_DIR files
    if [ -n "$(ls -A $REALTIME_DIR 2>/dev/null)" ]; then
        for file in "$REALTIME_DIR"/*; do
            if [ -f "$file" ]; then
                filename=$(basename "$file")
                # skip raw BGP updates
                if [ "$filename" != "bgp_updates.bin" ]; then
                    cp "$file" "$READY_DIR/$filename"
                    : >"$file"
                fi
            fi
        done
    fi

    # 3. Process BGP dump files
    latest_bgp_file=$(get_latest_closed_bgp_dump)
    if [ $? -eq 0 ] && [ -f "$latest_bgp_file" ]; then
        # Copy the second latest BGP dump file to ready dir
        cp "$latest_bgp_file" "$READY_DIR/bgp_updates.bin"
    fi

    # 4. Clean up old BGP dump files
    bgp_files=($(ls -1 "$BGP_DUMP_DIR"/bgp_updates_*.bin 2>/dev/null | sort -r))
    bgp_count=${#bgp_files[@]}
    bgp_excess=$((bgp_count - MAX_BGP_DUMPS))
    if [ "$bgp_excess" -gt 0 ]; then
        for ((i = MAX_BGP_DUMPS; i < bgp_count; i++)); do
            rm -f "${bgp_files[$i]}"
        done
    fi

    # 5. Archive to history dir + clean up old history
    HISTORY_PATH="$HISTORY_DIR/$version"
    mkdir -p "$HISTORY_PATH"
    cp -r "$READY_DIR"/* "$HISTORY_PATH"/
    
    # clean history dir
    versions=($(ls -1 $HISTORY_DIR 2>/dev/null | sort -n))
    count=${#versions[@]}
    excess=$((count - MAX_HISTORY))
    # del old batch files
    if [ $excess -gt 0 ]; then
        for ((i = 0; i < excess; i++)); do
            rm -rf "$HISTORY_DIR/${versions[$i]}"
        done
    fi

    # 6. Wait for next batch
    ELAPSED=$(($(date +%s) - start_time))
    REMAINING=$((20 - ELAPSED))
    [ $REMAINING -gt 0 ] && sleep $REMAINING
done