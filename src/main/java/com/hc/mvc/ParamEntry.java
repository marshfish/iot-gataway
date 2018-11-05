package com.hc.mvc;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 设备公共参数
 */
@Slf4j
@Getter
public class ParamEntry {
    private String uniqueId;
    private String instruction;

    public ParamEntry(String uniqueId, String instruction) {
        this.uniqueId = uniqueId;
        this.instruction = instruction;
    }
}
