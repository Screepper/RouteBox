package com.routebox.entity.index;

import com.routebox.entity.ad.ParsedPossibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @Description
 * @Date 2025/12/14
**/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DetectResultLog extends IndexBase{
    // Base info
    private String dataSource;
    private Boolean isAnomaly;

    // Anomaly Info
    private String topologyInfo;
    private String systemNetworkAnomaly;
    private String bgpAnomaly;
    private String routerAnomaly;
    private String pingAnomaly;
    private String topologyAnomaly;

    // LLM-Parsed Info
    private String[] directEvidence; // Direct evidence
    private String[] indirectEvidence; // Indirect evidence
    private ParsedPossibility[] possibilities; // Possibility of each method
    private String attackType; // Conclusion type, e.g. "attack"
    private Integer[] attackers; // Attacker nodeIds
    private Integer[] victims; // Victim nodeIds
    private String anomalyStart;
    private String anomalyEnd;
}
