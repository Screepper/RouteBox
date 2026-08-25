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
    "./traffic_service.py"
)
mkdir -p $REALTIME_DIR $READY_DIR $HISTORY_DIR

# obtain and increment the version number
next_version() {
    if [ ! -f "$VERSION_FILE" ] || [ ! -s "$VERSION_FILE" ]; then
        echo "0" >"$VERSION_FILE" # ensure file exists
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
        if [[ "$script" == *.py ]]; then
            process_count=$(pgrep -fc "python3[[:space:]]+$script$") # python3
        else
            process_count=$(pgrep -fc "bash[[:space:]]+$script$") # bash
        fi
        if [ $process_count -eq 0 ]; then
            if [[ "$script" == *.py ]]; then
                python3 "$script" >/dev/null 2>&1 &
            else
                bash "$script" >/dev/null 2>&1 &
            fi
        fi
    done
}
# loop
while true; do
    # start all processes
    check_processes
    version=$(next_version)
    start_time=$(date +%s)
    sleep 19
    end_time=$(date +%s)
    echo "${version}|${start_time}|${end_time}" >"$VERSION_LOCK"
    # copy the realtime logs to the ready dir, and clear the contents of files in the realtime dir
    if [ -n "$(ls -A $REALTIME_DIR 2>/dev/null)" ]; then
        for file in "$REALTIME_DIR"/*; do
            if [ -f "$file" ]; then
                cp "$file" "$READY_DIR/$(basename "$file")"
                : >"$file"
            fi
        done
    fi
    # create history dir with version file
    HISTORY_PATH="$HISTORY_DIR/$version"
    mkdir -p "$HISTORY_PATH"
    cp -r "$READY_DIR"/* "$HISTORY_PATH"/
    # clean history dir
    versions=($(ls -1 $HISTORY_DIR | sort -n))
    count=${#versions[@]}
    excess=$((count - MAX_HISTORY))
    # del old batch files
    if [ $excess -gt 0 ]; then
        for ((i = 0; i < excess; i++)); do
            rm -rf "$HISTORY_DIR/${versions[$i]}"
        done
    fi
    # wait for next batch
    ELAPSED=$(($(date +%s) - start_time))
    REMAINING=$((20 - ELAPSED))
    [ $REMAINING -gt 0 ] && sleep $REMAINING
done
