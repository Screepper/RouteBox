package com.routebox.entity.topology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @Description SERNES Nodes Network Status
 * @Date 2025/4/18
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodesNet implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    
    private Boolean enable;
    private List<Integer> node_ids;
}
