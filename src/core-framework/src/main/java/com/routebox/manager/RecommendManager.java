package com.routebox.manager;

import com.routebox.entity.topology.DTO.LinkDTO;
import com.routebox.entity.topology.Recommended;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class RecommendManager {

    private static final int DEFAULT_TOP_K = 10;

    /**
     * @Description Recommend BGP targets
     * @Date 2025/3/27
     **/
    public static List<Recommended> recommendBgpTargets(int attackerId) {
        HashMap<Integer, List<Integer>> adjacencyList = TopologyManager.topologyInfo.getAdjacencyList();
        List<Integer> asNodeIds = TopologyManager.topologyInfo.getAsNodeIds();

        Map<Integer, Float> nodeValueMap = calculateNodeValues(adjacencyList);
        Map<Integer, Integer> apdFromAttacker = calculateAPD(attackerId, adjacencyList, asNodeIds);
        Map<Integer, Float> impactRatioFromAttacker = new HashMap<>();
        int maxAPD = apdFromAttacker.values().stream().max(Integer::compare).orElse(0);

        Map<Integer, Float> impactScoreMap = new HashMap<>();
        for (int targetId : asNodeIds) {
            if (targetId == attackerId) continue;

            Map<Integer, Integer> apdFromTarget = calculateAPD(targetId, adjacencyList, asNodeIds);

            float impactScore = 0f;
            int impactedCount = 0;
            for (int nodeId : asNodeIds) {
                if (nodeId == attackerId || nodeId == targetId) continue;

                if (apdFromAttacker.get(nodeId) < apdFromTarget.get(nodeId)) {
                    impactScore += nodeValueMap.get(nodeId);
                    impactedCount++;
                }
            }
            impactScoreMap.put(targetId, impactScore);
            impactRatioFromAttacker.put(targetId, impactedCount / (asNodeIds.size() - 1f)); // exclude attacker only
        }

        float maxImpactScore = impactScoreMap.values().stream().max(Float::compare).orElse(1f);
        float minImpactScore = impactScoreMap.values().stream().min(Float::compare).orElse(0f);
        float impactRange = maxImpactScore - minImpactScore;

        List<Recommended> results = new ArrayList<>();
        for (int targetId : asNodeIds) {
            if (targetId == attackerId) continue;

            float normalizedImpactScore = impactRange > 0 ? (impactScoreMap.get(targetId) - minImpactScore) / impactRange : 0.5f;
            int apdAttackerToTarget = apdFromAttacker.get(targetId);
            float successScore = 1f - (float) apdAttackerToTarget / (maxAPD + 1);
            float score = 0.6f * normalizedImpactScore + 0.4f * successScore;
            float impactRatio = impactRatioFromAttacker.get(targetId);

            Recommended rec = new Recommended();
            rec.setNodeId(targetId);
            rec.setScore(score);
            rec.setExpectedImpactRatio(impactRatio);
            results.add(rec);
        }

        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return results.subList(0, Math.min(DEFAULT_TOP_K, results.size()));
    }

    /**
     * @Description Recommend node targets
     * @Date 2025/3/27
     **/
    public static List<Recommended> recommendNodeTargets() {
        HashMap<Integer, List<Integer>> adjacencyList = TopologyManager.topologyInfo.getAdjacencyList();
        List<Integer> asNodeIds = TopologyManager.topologyInfo.getAsNodeIds();
        int totalNodeCount = asNodeIds.size();

        Map<Integer, Float> nodeValueMap = calculateNodeValues(adjacencyList);

        List<Recommended> results = new ArrayList<>();
        for (Integer nodeId : asNodeIds) {
            float nodeValue = nodeValueMap.get(nodeId);
            int degree = adjacencyList.get(nodeId).size();
            float impactRatio = (float) (1 + degree) / totalNodeCount;

            Recommended rec = new Recommended();
            rec.setNodeId(nodeId);
            rec.setScore(nodeValue);
            rec.setExpectedImpactRatio(impactRatio);
            results.add(rec);
        }

        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return results.subList(0, Math.min(DEFAULT_TOP_K, results.size()));
    }

    /**
     * @Description Recommend link targets
     * @Date 2025/3/27
     **/
    public static List<Recommended> recommendLinkTargets() {
        HashMap<Integer, List<Integer>> adjacencyList = TopologyManager.topologyInfo.getAdjacencyList();
        List<LinkDTO> links = TopologyManager.topologyInfo.getLinks();
        int totalNodeCount = TopologyManager.topologyInfo.getNodeIds().size();

        Map<Integer, Float> nodeValueMap = calculateNodeValues(adjacencyList);
        List<Recommended> results = new ArrayList<>();

        for (LinkDTO link : links) {
            int sourceId = link.getSourceId();
            int targetId = link.getTargetId();

            String linkId = link.getId();

            float linkScore = nodeValueMap.get(sourceId) + nodeValueMap.get(targetId);
            int degreeU = adjacencyList.get(sourceId).size();
            int degreeV = adjacencyList.get(targetId).size();
            float impactRatio = (float) (2 + degreeU + degreeV) / totalNodeCount;

            Recommended rec = new Recommended();
            rec.setLinkId(linkId);
            rec.setScore(linkScore);
            rec.setExpectedImpactRatio(impactRatio);
            results.add(rec);
        }

        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return results.subList(0, Math.min(DEFAULT_TOP_K, results.size()));
    }

    /**
     * @Description Calculate BGP attack score by attacker and target
     * @Date 2025/3/27
     **/
    public static Float calcBgpAttackScore(int attackerId, int targetId) {
        HashMap<Integer, List<Integer>> adjacencyList = TopologyManager.topologyInfo.getAdjacencyList();
        List<Integer> asNodeIds = TopologyManager.topologyInfo.getAsNodeIds();

        Map<Integer, Float> nodeValueMap = calculateNodeValues(adjacencyList);
        Map<Integer, Integer> apdFromAttacker = calculateAPD(attackerId, adjacencyList, asNodeIds);
        Map<Integer, Integer> apdFromTarget = calculateAPD(targetId, adjacencyList, asNodeIds);

        int impactedCount = 0;
        for (int nodeId : asNodeIds) {
            if (nodeId == attackerId || nodeId == targetId) continue;
            if (apdFromAttacker.get(nodeId) < apdFromTarget.get(nodeId)) {
                impactedCount++;
            }
        }

        float maxImpactScore = 0f;
        float minImpactScore = Float.MAX_VALUE;
        for (int t : asNodeIds) {
            if (t == attackerId) continue;
            Map<Integer, Integer> apdFromT = calculateAPD(t, adjacencyList, asNodeIds);
            float tempImpactScore = 0f;
            for (int nodeId : asNodeIds) {
                if (nodeId == attackerId || nodeId == t) continue;
                if (apdFromAttacker.get(nodeId) < apdFromT.get(nodeId)) {
                    tempImpactScore += nodeValueMap.get(nodeId);
                }
            }
            maxImpactScore = Math.max(maxImpactScore, tempImpactScore);
            minImpactScore = Math.min(minImpactScore, tempImpactScore);
        }

        return (float) impactedCount / (asNodeIds.size() - 1);
    }

    /**
     * @Description Calculate node attack score by target
     * @Date 2025/3/27
     **/
    public static Float calcNodeAttackScore(int targetId) {
        HashMap<Integer, List<Integer>> adjacencyList = TopologyManager.topologyInfo.getAdjacencyList();
        List<Integer> asNodeIds = TopologyManager.topologyInfo.getAsNodeIds();
        int totalNodeCount = asNodeIds.size();
        int degree = adjacencyList.get(targetId).size();

        return (float) (1 + degree) / totalNodeCount;
    }

    /**
     * @Description Calculate link attack score by link
     * @Date 2025/3/27
     **/
    public static Float calcLinkAttackScore(String linkId) {
        HashMap<Integer, List<Integer>> adjacencyList = TopologyManager.topologyInfo.getAdjacencyList();
        List<LinkDTO> links = TopologyManager.topologyInfo.getLinks();
        int totalNodeCount = TopologyManager.topologyInfo.getNodeIds().size();

        int sourceId = -1;
        int targetId = -1;
        for (LinkDTO link : links) {
            if (link.getId().equals(linkId)) {
                sourceId = link.getSourceId();
                targetId = link.getTargetId();
                break;
            }
        }

        if (sourceId == -1 || targetId == -1) return null;
        int degreeU = adjacencyList.get(sourceId).size();
        int degreeV = adjacencyList.get(targetId).size();

        return (float) (2 + degreeU + degreeV) / totalNodeCount;
    }

    /**
     * @Description Calculate node values (Degree and Betweenness Centrality)
     * @Date 2025/3/27
     **/
    private static Map<Integer, Float> calculateNodeValues(HashMap<Integer, List<Integer>> adjacencyList) {
        Map<Integer, Integer> degreeMap = new HashMap<>();
        Map<Integer, Float> betweennessMap = calculateBetweennessScores(adjacencyList);
        adjacencyList.forEach((key, value) -> degreeMap.put(key, value.size()));

        float maxDegree = degreeMap.values().stream().max(Integer::compare).orElse(1);
        float minDegree = degreeMap.values().stream().min(Integer::compare).orElse(0);
        float maxBetweenness = betweennessMap.values().stream().max(Float::compare).orElse(1f);
        float minBetweenness = betweennessMap.values().stream().min(Float::compare).orElse(0f);

        Map<Integer, Float> nodeValueMap = new HashMap<>();
        for (Integer nodeId : adjacencyList.keySet()) {
            float normalizedDegree = (degreeMap.get(nodeId) - minDegree) / (maxDegree - minDegree);
            float normalizedBetweenness = (betweennessMap.get(nodeId) - minBetweenness) / (maxBetweenness - minBetweenness);

            float nodeValue = 0.6f * normalizedDegree + 0.4f * normalizedBetweenness;
            nodeValueMap.put(nodeId, nodeValue);
        }

        return nodeValueMap;
    }

    /**
     * @Description Calculate betweenness Centrality
     * @Date 2025/3/27
     **/
    private static Map<Integer, Float> calculateBetweennessScores(HashMap<Integer, List<Integer>> adjacencyList) {
        Map<Integer, Float> betweennessMap = new HashMap<>();
        adjacencyList.keySet().forEach(node -> betweennessMap.put(node, 0.0f));

        // traverse each node as source node s
        for (Integer s : adjacencyList.keySet()) {
            // === Phase 1: BFS from source node s ===
            Stack<Integer> stack = new Stack<>();
            Map<Integer, List<Integer>> predecessors = new HashMap<>(); // predecessors of each node v
            Map<Integer, Integer> distance = new HashMap<>(); // distance from source node s to each node v
            Map<Integer, Float> sigma = new HashMap<>(); // number of shortest paths from source node s to each node v
            Map<Integer, Float> delta = new HashMap<>(); // dependency of each node v on source node s
            // initialize data structures
            adjacencyList.keySet().forEach(v -> {
                predecessors.put(v, new ArrayList<>());
                distance.put(v, -1); // -1 means unvisited
                sigma.put(v, 0.0f);
                delta.put(v, 0.0f);
            });
            // source node s to itself is distance 0, sigma = 1
            distance.put(s, 0);
            sigma.put(s, 1.0f);

            Queue<Integer> queue = new LinkedList<>();
            queue.add(s);
            // BFS
            while (!queue.isEmpty()) {
                Integer v = queue.poll();
                stack.push(v);
                for (Integer w : adjacencyList.get(v)) {
                    // first time discovering w
                    if (distance.get(w) < 0) {
                        distance.put(w, distance.get(v) + 1);
                        queue.add(w);
                    }
                    if (distance.get(w) == distance.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        predecessors.get(w).add(v); // v is a predecessor of w
                    }
                }
            }

            // === Phase 2: Backtrack to calculate delta ===
            while (!stack.isEmpty()) {
                Integer w = stack.pop();
                // traverse all predecessors of w
                for (Integer v : predecessors.get(w)) {
                    float sigmaW = sigma.get(w);
                    // prevent division by zero error
                    if (sigmaW == 0) continue;
                    // calculate dependency of v on w：sigma[v] / sigma[w]
                    // then accumulate delta[w]
                    float ratio = sigma.get(v) / sigmaW;
                    delta.put(v, delta.get(v) + ratio * (1 + delta.get(w)));
                }

                // if w is not source node s, accumulate delta[w] to betweennessMap
                if (!w.equals(s)) {
                    betweennessMap.put(w, betweennessMap.get(w) + delta.get(w));
                }
            }
        }

        // Brandes algorithm finally divides by 2 times
        betweennessMap.replaceAll((node, value) -> value / 2);

        return betweennessMap;
    }

    /**
     * @Description Calculate AS Path Distance via Breadth-First Search
     * @Date 2025/3/27
     **/
    private static Map<Integer, Integer> calculateAPD(int sourceId, HashMap<Integer, List<Integer>> adjacencyList, List<Integer> nodeIds) {
        Map<Integer, Integer> distance = new HashMap<>();
        Set<Integer> nodeSet = new HashSet<>(nodeIds);

        nodeIds.forEach(v -> distance.put(v, -1));
        distance.put(sourceId, 0);

        Queue<Integer> queue = new LinkedList<>();
        queue.add(sourceId);

        while (!queue.isEmpty()) {
            Integer v = queue.poll();
            List<Integer> neighbors = adjacencyList.get(v);
            if (neighbors == null) continue;

            for (Integer w : neighbors) {
                if (!nodeSet.contains(w)) continue;
                if (distance.get(w) < 0) {
                    distance.put(w, distance.get(v) + 1);
                    queue.add(w);
                }
            }
        }

        return distance;
    }
}