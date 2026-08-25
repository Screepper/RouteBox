package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description Node Adjacency Info
 * @Date 2025/7/4
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdjacencyInfo {
    private Integer nodeId; // Node ID
    private List<Integer> adjacentNodeIds; // Adjacent Node ID List
}
