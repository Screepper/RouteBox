package com.routebox.entity.attack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description
 * @Date 2026/4/23
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttackRatioCalc {
    private Integer attackCategory;
    private List<Integer> attackerIds;
    private List<Object> targetIds;
}
