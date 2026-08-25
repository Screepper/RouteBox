package com.routebox.common.response;

import com.routebox.common.enums.DetectStage;
import com.routebox.util.TimeUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WsR {
    private int stage;
    private String mes;
    private String time;

    public WsR(DetectStage detectStage, String mes) {
        this.stage = detectStage.getStage();
        this.mes = mes;
        this.time = TimeUtil.getNowTimeStr();
    }

    public WsR(DetectStage detectStage) {
        this.stage = detectStage.getStage();
        this.time = TimeUtil.getNowTimeStr();
    }
}