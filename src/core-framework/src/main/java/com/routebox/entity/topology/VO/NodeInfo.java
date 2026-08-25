package com.routebox.entity.topology.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @Description
 * @Date 2025/12/12
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Integer nodeId;
    private String name;
    private String type;
    private String status;
    private List<String> neighborVethInfo;
    private String pingableIp;
}
