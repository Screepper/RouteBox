package com.routebox.entity.topology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @Description Node Network Status
 * @Date 2025/4/18
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeNet implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Integer nodeId;
    private Boolean isOnline;
}
