package com.routebox.entity.topology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @Description Recommend Node Info
 * @Date 2025/4/26
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Recommended implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Integer nodeId;
    private String linkId; // nodeId and linkId, only one is not null
    private Float score;
    private Float expectedImpactRatio;
}
