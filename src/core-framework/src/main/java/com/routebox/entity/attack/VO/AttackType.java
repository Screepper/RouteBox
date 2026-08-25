package com.routebox.entity.attack.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description
 * @Date 2026/3/8
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttackType {
    private String belongTo;
    private String type;
    private String description;
    private int serialNumber;
}
