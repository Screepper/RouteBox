package com.routebox.entity.attack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description
 * @Date 2026/4/22
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FetchAttackRecommendation {
    private Integer attackCategory;
    private Integer attackerId;
}
