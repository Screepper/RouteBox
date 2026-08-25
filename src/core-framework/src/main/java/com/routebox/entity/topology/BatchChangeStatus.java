package com.routebox.entity.topology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchChangeStatus {
    private List<Integer> node_ids;
    private String action;
}