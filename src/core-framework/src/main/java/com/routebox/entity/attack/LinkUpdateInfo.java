package com.routebox.entity.attack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @Description SERNES Link Update Info
 * @Date 2025/6/26
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkUpdateInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String op; // Operation type
    private LinkControlArgs args; // Link control arguments
    private List<String> link_ids; // LinkId list
}