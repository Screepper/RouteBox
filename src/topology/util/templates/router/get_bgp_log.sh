#!/bin/bash

LOG_FOLDER="/usr/routebox/logs/realtime"
LOG_FILE="${LOG_FOLDER}/bgp.log"

while true; do
  # ensure can store complete log structure while copying logs (masker and Masker are separators used for subsequent parsing)
  echo -e "$(date +%s)masker$(vtysh -c "show ip bgp summary")masker$(vtysh -c "show ip bgp")masker$(vtysh -c "show running-config")Masker" >> "${LOG_FILE}"
  sleep 15
done