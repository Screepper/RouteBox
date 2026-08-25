package com.routebox.entity.sernes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description Normal SERNES Web Socket Response
 * @Date 2025/4/4
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WsResponse {
    private String type;
    private String data;
}
