package com.routebox.entity.sernes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description
 * @Date 2026/5/10
 * @Author Screep
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HttpResponse {
    private String status;
    private Object data;
}
