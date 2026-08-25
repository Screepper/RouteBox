package com.routebox.entity.attack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description Link-like Attacks Parameters
 * @Date 2025/6/26
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkAttackInfo {
    private List<String> targetIds; // LinkId list
}
