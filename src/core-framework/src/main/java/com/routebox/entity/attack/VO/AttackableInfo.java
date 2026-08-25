package com.routebox.entity.attack.VO;

import com.routebox.entity.topology.VO.TopologySummary;
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
public class AttackableInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private TopologySummary topologySummary;
    private List<AttackType> supportAttackTypes;
    private RunningAttackSummary runningAttackSummary;
}