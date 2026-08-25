package com.routebox.entity.attack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description Base Attack Info
 * @Date 2025/4/25
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeAttackInfo {
    private List<Integer> targetIds; // Target nodeId list
}
