package com.routebox.entity.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description Elasticsearch Entity: Attack Log
 * @Date 2025/5/27
**/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttackLog extends IndexBase {
    private Long endTimestamp; // Attack End Timestamp
    private String attackType; // Attack Type
    private List<Integer> attackers; // Attackers' NodeIds
    private List<Integer> victims; // Victims' NodeIds
    private String attackProcess; // Attack Process
}
