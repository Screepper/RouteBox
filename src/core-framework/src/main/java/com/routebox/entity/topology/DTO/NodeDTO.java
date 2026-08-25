package com.routebox.entity.topology.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description SERNES Node Info
 * @Date 2025/3/27
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeDTO {
    private Integer id;
    private String name;
    private String type;
    private String alias;
    private String status;
    private Boolean physical;
    private Double[] x; // The center point in the canvas
    private Double[] y; // The resolution height
}
