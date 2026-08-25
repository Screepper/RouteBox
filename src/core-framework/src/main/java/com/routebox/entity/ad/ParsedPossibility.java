package com.routebox.entity.ad;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Parsed Anomaly Possibility
 * @Date 2025/11/11
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParsedPossibility {
    private String method; // Method name
    private String possibility; // Possibility of the method
    private String rationale; // Rationale for the possibility
}
