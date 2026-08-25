package com.routebox.entity.attack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description BGP-like Attacks Parameters
 * @Date 2025/3/29
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BgpAttackInfo{
    private Integer attackerId; // Attacker nodeId
    private List<Integer> targetIds;
}
