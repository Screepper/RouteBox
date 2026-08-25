package com.routebox.entity.attack.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @Description
 * @Date 2026/3/13
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ForceRevokeInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String attackType;
    private String attackerName;
    private List<String> victimNames;
}
