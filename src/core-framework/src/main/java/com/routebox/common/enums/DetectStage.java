package com.routebox.common.enums;

import lombok.Getter;

@Getter
public enum DetectStage {
    WAIT(-1),
    START(0),
    PROCESS_DATA(1),
    ANOMALY_DETECT(2),
    DETECT_RESULT(3),
    STOP(4);

    private final int stage;

    DetectStage(int stage) {
        this.stage = stage;
    }
}