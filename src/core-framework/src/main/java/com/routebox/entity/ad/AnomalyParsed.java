package com.routebox.entity.ad;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description
 * @Date 2025/12/15
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnomalyParsed {
    private String[] directEvidence; // Direct evidence
    private String[] indirectEvidence; // Indirect evidence
    private ParsedPossibility[] possibilities; // Possibility of each method
    private String attackType; // Conclusion type, e.g. "attack"
    private Integer[] attackers; // Attacker nodeIds
    private Integer[] victims; // Victim nodeIds
    private String anomalyStart;
    private String anomalyEnd;
}
