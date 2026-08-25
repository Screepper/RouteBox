package com.routebox.entity.attack.VO;

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
public class RunningAttackSummary implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String eventId; // Attack eventId
    private Long startTime; // Attack start time
}
