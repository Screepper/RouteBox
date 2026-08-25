package com.routebox.manager.anomaly;

import com.routebox.common.enums.DetectStage;
import com.routebox.common.response.WsR;
import com.routebox.entity.ad.AnomalyInfo;
import com.routebox.util.TimeUtil;
import com.routebox.util.WsMesUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Description
 * @Date 2026/4/25
**/
public class SystemNetworkAnomalyParser {
    public static String postParse(List<AnomalyInfo> anomalyInfos) {
        StringBuilder sb = new StringBuilder();
        Map<Integer, List<AnomalyInfo>> nodeAnomalyMap = anomalyInfos.stream().collect(Collectors.groupingBy(AnomalyInfo::getNodeId));

        // Sort nodeIds in ascending order
        List<Integer> sortedNodeIds = new ArrayList<>(nodeAnomalyMap.keySet());
        sortedNodeIds.sort(Comparator.naturalOrder());


        for (Integer nodeId : sortedNodeIds) {
            sb.append("Node").append(nodeId).append("\n");

            List<AnomalyInfo> nodeAnomalies = nodeAnomalyMap.get(nodeId);
            nodeAnomalies.sort(Comparator.comparing(AnomalyInfo::getStartTime));
            Map<String, List<AnomalyInfo>> groupedAnomalies = new HashMap<>();

            for (AnomalyInfo anomaly : nodeAnomalies) {
                String direction = anomaly.getDescription().contains("decreased") ? "decreased" : "increased";
                String groupKey = anomaly.getStartTime() + "_" + anomaly.getEndTime() + "_" + direction;
                groupedAnomalies.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(anomaly);
            }

            List<List<AnomalyInfo>> groupsList = new ArrayList<>(groupedAnomalies.values());

            for (List<AnomalyInfo> group : groupsList) {
                List<String> fields = group.stream().map(AnomalyInfo::getFieldName).toList();
                List<String> percentages = group.stream()
                        .map(anomaly -> {
                            Matcher matcher = Pattern.compile("-?\\d+\\.\\d+%").matcher(anomaly.getDescription());
                            return matcher.find() ? matcher.group() : "";
                        }).toList();

                AnomalyInfo first = group.getFirst();
                String timeStr = TimeUtil.timestamp10ToTimeStr(first.getStartTime()) + "-" + TimeUtil.timestamp10ToTimeStr(first.getEndTime());
                String direction = first.getDescription().contains("decreased") ? "decreased" : "increased";

                sb.append("[").append(String.join(", ", fields)).append(" ").append(timeStr).append("]: ");

                if (group.size() == 1) {
                    sb.append(first.getDescription());
                } else {
                    sb.append(direction).append(" [").append(String.join(", ", percentages)).append("]");
                }

                sb.append("\n");
            }
        }

        String sbStr = sb.toString();
        WsMesUtil.push(new WsR(DetectStage.DETECT_RESULT, sbStr));

        return sbStr;
    }
}
