package com.routebox.entity.topology.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @Description
 * @Date 2025/12/12
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String linkId;
    private Integer srcNodeId;
    private Integer dstNodeId;
    private String status;
}
