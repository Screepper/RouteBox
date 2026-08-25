package com.routebox.entity.topology.DTO;

import com.routebox.entity.topology.NodeNet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @Description Nodes Network Status
 * @Date 2025/4/18
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeNetDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private List<NodeNet> nodesNet;
}
