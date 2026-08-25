package com.routebox.manager.anomaly;

import com.routebox.entity.index.RouterLog;
import com.routebox.entity.index.auxiliary.VtyshInfo;
import com.routebox.util.TimeUtil;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @Description Router Command Anomaly Constructor
 * @Date 2025/11/24
**/
public class RouterAnomalyParser extends AnomalyParser {
    private static final int MAX_DISPLAY_COUNT = 20;
    private static final int MAX_UP_DISPLAY_COUNT = 8;
    private static final int MAX_DOWN_DISPLAY_COUNT = 8;
    private static final int MAX_TOTAL_DISPLAY_COUNT = MAX_UP_DISPLAY_COUNT + MAX_DOWN_DISPLAY_COUNT;

    // Blacklist
    private static final List<Pattern> configChangePatterns = Arrays.asList(
            Pattern.compile("network\\s+"),
            Pattern.compile("interface\\s+"),
            Pattern.compile("ip\\s+address\\s+"),
            Pattern.compile("shutdown"),
            Pattern.compile("vlan\\s+"),
            Pattern.compile("route-map\\s+"),
            Pattern.compile("set\\s+"),
            Pattern.compile("match\\s+"),
            Pattern.compile("router\\s+bgp\\s+"),
            Pattern.compile("neighbor\\s+"),
            Pattern.compile("bgp\\s+"),
            Pattern.compile("ip\\s+route\\s+"),
            Pattern.compile("redistribute\\s+"),
            Pattern.compile("access-list\\s+"),
            Pattern.compile("ip\\s+prefix-list\\s+"),
            Pattern.compile("community-list\\s+"),
            Pattern.compile("hostname\\s+"),
            Pattern.compile("password\\s+"),
            Pattern.compile("line\\s+vty\\s+")
    );

    // Whitelist
    private static final List<Pattern> viewOnlyPatterns = Arrays.asList(
            Pattern.compile("show\\s+"),
            Pattern.compile("ping\\s+"),
            Pattern.compile("traceroute\\s+"),
            Pattern.compile("echo\\s+"),
            Pattern.compile("enable\\s*$"),
            Pattern.compile("do\\s+show\\s+"),
            Pattern.compile("exit\\s*$"),
            Pattern.compile("quit\\s*$")
    );

    public static String parse(Map<Integer, List<RouterLog>> routerLogMap) {
        StringBuilder sb = new StringBuilder();
        if (routerLogMap == null || routerLogMap.isEmpty()) return null;

        routerLogMap.forEach((nodeId, logs) -> {
            if (logs == null || logs.isEmpty()) return;

            // Collect all historical commands and group by time
            Map<Long, List<String>> timeToCommands = new TreeMap<>();
            for (RouterLog log : logs) {
                if (log.getHistories() == null) continue;

                for (VtyshInfo history : log.getHistories()) {
                    String command = history.getCommand();
                    if (command == null || command.trim().isEmpty()) continue;

                    // Filter out view-only commands
                    boolean isViewOnly = false;
                    for (Pattern pattern : viewOnlyPatterns) {
                        if (pattern.matcher(command).find()) {
                            isViewOnly = true;
                            break;
                        }
                    }

                    if (isViewOnly) continue;

                    // Check if it's a configuration change command
                    boolean isConfigChange = false;
                    for (Pattern pattern : configChangePatterns) {
                        if (pattern.matcher(command).find()) {
                            isConfigChange = true;
                            break;
                        }
                    }

                    if (isConfigChange) {
                        String cleanCommand = command.replaceAll("[\\[\\]]", "").trim();
                        timeToCommands.computeIfAbsent(history.getTime(), k -> new ArrayList<>()).add(cleanCommand);
                    }
                }
            }

            List<Map.Entry<Long, List<String>>> timeEntries = new ArrayList<>(timeToCommands.entrySet());
            int totalTimeEntries = timeEntries.size();

            if (totalTimeEntries > MAX_TOTAL_DISPLAY_COUNT) {
                for (int i = 0; i < MAX_UP_DISPLAY_COUNT; i++) {
                    Map.Entry<Long, List<String>> entry = timeEntries.get(i);
                    buildOmittedCommands(sb, nodeId, entry);
                }

                int omitted = totalTimeEntries - MAX_TOTAL_DISPLAY_COUNT;
                sb.append(omitted).append(" more commands records...\n");
                for (int i = totalTimeEntries - MAX_DOWN_DISPLAY_COUNT; i < totalTimeEntries; i++) {
                    Map.Entry<Long, List<String>> entry = timeEntries.get(i);
                    buildOmittedCommands(sb, nodeId, entry);
                }
            } else {
                for (Map.Entry<Long, List<String>> entry : timeEntries) {
                    buildOmittedCommands(sb, nodeId, entry);
                }
            }
        });

        return sb.toString();
    }

    private static void buildOmittedCommands(StringBuilder sb, Integer nodeId, Map.Entry<Long, List<String>> entry) {
        long time = entry.getKey();
        List<String> commands = entry.getValue();
        String timeStr = TimeUtil.timestamp10ToTimeStr(time);
        List<String> displayCommands = commands.stream().limit(MAX_DISPLAY_COUNT).toList();
        StringBuilder commandSb = new StringBuilder(String.join(", ", displayCommands));

        if (commands.size() > MAX_DISPLAY_COUNT) {
            commandSb.append(", and other ").append(commands.size() - MAX_DISPLAY_COUNT).append(" commands");
        }

        sb.append(getNodeType(nodeId)).append(nodeId)
                .append(" execute [").append(commandSb)
                .append("] at ").append(timeStr).append("\n");
    }
}