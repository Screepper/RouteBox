package com.routebox.entity.topology.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description SERNES Link Info
 * @Date 2025/3/27
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkDTO {
    private String id;
    @JsonProperty("source_id")
    private Integer sourceId;
    @JsonProperty("target_id")
    private Integer targetId;
    private String status;
}
