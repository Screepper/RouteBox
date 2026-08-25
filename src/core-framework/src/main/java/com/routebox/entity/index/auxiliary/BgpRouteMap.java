package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description BGP Routing Map Info
 * @Date 2025/7/25
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BgpRouteMap {
    private String name; // BGP Route Map Name
    private String action; // Action Type (permit/deny)
    private Integer sequenceNumber; // Rule Execution Sequence
    private List<String> matchStatements = new ArrayList<>(); // Match Conditions List
    private List<String> setStatements = new ArrayList<>(); // Action List

    public BgpRouteMap(String name, String action, Integer sequenceNumber) {
        this.name = name;
        this.action = action;
        this.sequenceNumber = sequenceNumber;
    }

    public void addMatchStatement(String statement) {
        matchStatements.add(statement);
    }

    public void addSetStatement(String statement) {
        setStatements.add(statement);
    }
}
