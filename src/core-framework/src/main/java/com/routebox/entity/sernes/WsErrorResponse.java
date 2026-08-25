package com.routebox.entity.sernes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Abnormal SERNES Web Socket Response
 * @Date 2025/4/4
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WsErrorResponse {
    private String type;
    private String msg;
}
