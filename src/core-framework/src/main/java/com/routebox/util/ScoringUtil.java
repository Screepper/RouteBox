package com.routebox.util;

import com.routebox.entity.ad.AnomalyParsed;
import com.routebox.entity.index.AttackLog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @Description Scoring Tool
 * @Date 2025/11/10
**/
@Component
@Slf4j
public class ScoringUtil {
    private static final Map<String, List<TypeScore>> ATTACK_SCORE_RULES = new HashMap<>() {{
        put("Prefix Hijacking", List.of( // Prefix Hijacking
                new TypeScore("^prefix.*hijack.*", 30),
                new TypeScore(".*hijack.*prefix.*", 25),
                new TypeScore(".*prefix.*hijack.*", 25),
                new TypeScore(".*hijack.*", 20),
                new TypeScore(".*prefix.*", 15),
                new TypeScore(".*rout.*", 10),
                new TypeScore(".*link.*", 5)
        ));
        put("Sub-prefix Hijacking", List.of( // Sub-prefix Hijacking
                new TypeScore(".*sub.*prefix.*hijack.*", 30),
                new TypeScore(".*hijack.*prefix.*", 25),
                new TypeScore(".*prefix.*hijack.*", 25),
                new TypeScore(".*hijack.*", 20),
                new TypeScore(".*prefix.*", 15),
                new TypeScore(".*rout.*", 10),
                new TypeScore(".*link.*", 5)
        ));
        put("Route Flapping", List.of( // Route Flapping
                new TypeScore(".*route.*flap.*", 30),
                new TypeScore(".*flap.*rout.*", 25),
                new TypeScore(".*flap.*", 15),
                new TypeScore(".*oscillat.*", 15),
                new TypeScore(".*shock.*", 15),
                new TypeScore(".*rout.*", 10),
                new TypeScore(".*link.*", 5)
        ));
        put("Route Leakage", List.of( // Route Leakage
                new TypeScore(".*route.*leak.*", 30),
                new TypeScore(".*leak.*", 25),
                new TypeScore(".*rout.*", 10),
                new TypeScore(".*link.*", 5)
        ));


        put("Node Down", List.of( // Node Down
                new TypeScore(".*node.*down.*", 30),
                new TypeScore(".*node.*failure.*", 30),
                new TypeScore(".*down.*", 20),
                new TypeScore(".*failure.*", 20),
                new TypeScore(".*broken.*", 15),
                new TypeScore(".*break.*", 15),
                new TypeScore(".*system.*", 5),
                new TypeScore(".*link.*", 5)
        ));
        put("System Overload", List.of( // System Overload
                new TypeScore(".*system.*over.*", 30),
                new TypeScore(".*overload.*", 25),
                new TypeScore(".*system.*", 15),
                new TypeScore(".*node.*", 10),
                new TypeScore(".*ddos.*", 10),
                new TypeScore(".*distribut.*", 10),
                new TypeScore(".*jitter.*", 5),
                new TypeScore(".*link.*", 5)
        ));
        put("Memory Leakage", List.of( // Memory Leakage
                new TypeScore(".*memory.*leak.*", 30),
                new TypeScore(".*memory.*drain.*", 25),
                new TypeScore(".*over.*", 15),
                new TypeScore(".*system.*", 15),
                new TypeScore(".*node.*", 10),
                new TypeScore(".*ddos.*", 10),
                new TypeScore(".*distribut.*", 10),
                new TypeScore(".*jitter.*", 5),
                new TypeScore(".*link.*", 5)
        ));

        put("Link Down", List.of( // Link Down
                new TypeScore(".*link.*down.*", 30),
                new TypeScore(".*link.*failure.*", 30),
                new TypeScore(".*down.*", 20),
                new TypeScore(".*failure.*", 20),
                new TypeScore(".*link.*", 15),
                new TypeScore(".*broken.*", 15),
                new TypeScore(".*break.*", 15),
                new TypeScore(".*node.*", 10),
                new TypeScore(".*system.*", 5)
        ));
        put("Link Congestion", List.of( // Link Congestion
                new TypeScore(".*link.*congest.*", 30),
                new TypeScore(".*latency.*jitter.*", 25),
                new TypeScore(".*ddos.*", 25),
                new TypeScore(".*distribut.*", 25),
                new TypeScore(".*congest.*", 25),
                new TypeScore(".*jitter.*", 20),
                new TypeScore(".*link.*", 15),
                new TypeScore(".*node.*", 10),
                new TypeScore(".*system.*", 5)
        ));
        put("Link Jitter", List.of( // Link Jitter
                new TypeScore(".*link.*jitter.*", 30),
                new TypeScore(".*link.*congest.*", 25),
                new TypeScore(".*ddos.*", 25),
                new TypeScore(".*distribut.*", 25),
                new TypeScore(".*jitter.*", 25),
                new TypeScore(".*congest.*", 20),
                new TypeScore(".*link.*", 15),
                new TypeScore(".*node.*", 10),
                new TypeScore(".*system.*", 5)
        ));
    }};

    private static final Set<String> NON_ATTACKER_ATTACK_TYPES = new HashSet<>() {{
        add("Node Down");
        add("System Overload");
        add("Memory Leakage");
        add("Link Down");
        add("Link Congestion");
        add("Link Jitter");
    }};

    private static final long MIN_MINUTES = 2 * 60;
    private static final long MAX_MINUTES = 20 * MIN_MINUTES;


    /**
     * @Description Scoring attack result
     * @Date 2025/11/16
     **/
    public int scoring(AttackLog attackLog, AnomalyParsed anomalyParsed) {
        // Extract real attack info
        String realType = attackLog.getAttackType();
        List<Integer> realAttackers = attackLog.getAttackers() == null ? new ArrayList<>() : attackLog.getAttackers();
        List<Integer> realVictims = attackLog.getVictims() == null ? new ArrayList<>() : attackLog.getVictims();
        long realStart = attackLog.getTimestamp();
        long realEnd = attackLog.getEndTimestamp();
        String realTimeline = TimeUtil.timestamp10ToDateTimeStr(realStart) + " - " + TimeUtil.timestamp10ToDateTimeStr(realEnd);

        // Extract pred attack info
        String predType = anomalyParsed.getAttackType();
        List<Integer> predAttackers = anomalyParsed.getAttackers() == null ? new ArrayList<>() : Arrays.asList(anomalyParsed.getAttackers());
        List<Integer> predVictims = anomalyParsed.getVictims() == null ? new ArrayList<>() : Arrays.asList(anomalyParsed.getVictims());
        String predStartStr = anomalyParsed.getAnomalyStart();
        String predEndStr = anomalyParsed.getAnomalyEnd();
        String predTimeline = predStartStr + " - " + predEndStr;
        long predStart = TimeUtil.dateTimeStr2Timestamp10(predStartStr);
        long predEnd = TimeUtil.dateTimeStr2Timestamp10(predEndStr);

        // Parse and scoring
        ScoreResult scoreResult = calculateScore(
                realType, realAttackers, realVictims, realStart, realEnd,
                predType, predAttackers, predVictims, predStart, predEnd
        );

        int typeScore = scoreResult.getTypeScore();
        int attackersScore = scoreResult.getAttackerScore();
        int victimsScore = scoreResult.getVictimScore();
        int timeScore = scoreResult.getTimeScore();
        int totalScore = scoreResult.getTotalScore();

        log.info("===========================================================");
        log.info("\\ : Attack Type | Attackers | Victims | Timeline | Total |");
        log.info("Real : {} | {} | {} | {} | None |", realType, realAttackers, realVictims, realTimeline);
        log.info("Pred : {} | {} | {} | {} | None |", predType, predAttackers, predVictims, predTimeline);
        log.info("Score: {} | {} | {} | {} | {}", typeScore, attackersScore, victimsScore, timeScore, totalScore);
        log.info("===========================================================");

        return totalScore;
    }

    /**
     * @Description Automated Scoring
     * @Date 2025/11/10
     **/
    private ScoreResult calculateScore(String realType, List<Integer> realAttackers, List<Integer> realVictims, long realStart, long realEnd,
                                       String predType, List<Integer> predAttackers, List<Integer> predVictims, long predStart, long predEnd) {
        // calculate scores
        int typeScore = calculateTypeScore(realType, predType);
        int attackerScore = calculateAttackersScore(realType, realAttackers, predAttackers);
        int victimScore = calculateVictimsScore(realType, realVictims, predAttackers, predVictims);
        int timeScore = calculateTimeScore(realStart, realEnd, predStart, predEnd);

        return new ScoreResult(typeScore, attackerScore, victimScore, timeScore);
    }

    /**
     * @Description Calculate attack type score
     * @Date 2025/11/10
     **/
    private int calculateTypeScore(String realType, String predType) {
        if (realType == null || realType.trim().isEmpty() || predType == null) return 0;

        // Obtain the rating rules corresponding to the current real type
        List<TypeScore> rules = ATTACK_SCORE_RULES.getOrDefault(realType.trim(), Collections.emptyList());

        // Match the rule with the highest score
        int maxScore = 0;
        for (TypeScore rule : rules) {
            if (Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE).matcher(predType).matches()) {
                maxScore = Math.max(maxScore, rule.getScore());
            }
        }

        return maxScore;
    }

    /**
     * @Description Calculate attacker score
     * @Date 2025/11/10
     **/
    private int calculateAttackersScore(String realType, List<Integer> realAttackers, List<Integer> predAttackers) {
        if (NON_ATTACKER_ATTACK_TYPES.contains(realType)) return (predAttackers.isEmpty()) ? 20 : 10;

        if (realAttackers.isEmpty() && predAttackers.isEmpty()) return 20;
        if (realAttackers.isEmpty() || predAttackers.isEmpty()) return 0;

        Set<Integer> realSet = new HashSet<>(realAttackers);

        int hitCount = 0;
        for (int real : realSet) {
            if (new HashSet<>(predAttackers).contains(real)) {
                hitCount++;
            }
        }

        return (hitCount * 20) / realSet.size();
    }

    /**
     * @Description Calculate victim score
     * @Date 2025/11/10
     **/
    private int calculateVictimsScore(String realType, List<Integer> realVictims, List<Integer> predAttackers, List<Integer> predIncompleteVictims) {
        Set<Integer> predVictims = new HashSet<>(predIncompleteVictims);

        if (NON_ATTACKER_ATTACK_TYPES.contains(realType)) {
            predVictims.addAll(predAttackers);
        }

        if (realType.equals("Route Flapping")) return 20;
        else {
            if (realVictims.isEmpty() && predVictims.isEmpty()) return 20;
            if (realVictims.isEmpty() || predVictims.isEmpty()) return 0;

            Set<Integer> realSet = new HashSet<>(realVictims);

            int hitCount = 0;
            for (int real : realSet) {
                if (predVictims.contains(real)) {
                    hitCount++;
                }
            }

            return (hitCount * 20) / realSet.size();
        }
    }


    /**
     * @Description Calculate time range score
     * @Date 2025/11/10
     **/
    private int calculateTimeScore(long realStart, long realEnd, long predStart, long predEnd) {
        if (realStart <= 0 || realEnd <= 0 || predStart <= 0 || predEnd <= 0 || realStart >= realEnd) return 0;

        // Calculate time deviation
        long deltaStart = Math.abs(predStart - realStart);
        long deltaEnd = Math.abs(predEnd - realEnd);
        long totalDelta = deltaStart + deltaEnd;

        if (totalDelta <= MIN_MINUTES) return 30;
        else return (int) (30 - 30 * Math.min(1.0, (double) totalDelta / MAX_MINUTES));
    }

    /**
     * @Description Attack Type Rating Rule Model
     * @Date 2025/11/10
     **/
    @Data
    @AllArgsConstructor
    private static class TypeScore {
        private String pattern;
        private int score;
    }

    /**
     * @Description Rating Results Model
     * @Date 2025/11/10
     **/
    @Data
    private static class ScoreResult {
        private final int typeScore;       // 0-30
        private final int attackerScore;   // 0-20
        private final int victimScore;     // 0-20
        private final int timeScore;       // 0-30
        private final int totalScore;      // 0-100

        public ScoreResult(int typeScore, int attackerScore, int victimScore, int timeScore) {
            this.typeScore = typeScore;
            this.attackerScore = attackerScore;
            this.victimScore = victimScore;
            this.timeScore = timeScore;
            this.totalScore = typeScore + attackerScore + victimScore + timeScore;
        }
    }
}