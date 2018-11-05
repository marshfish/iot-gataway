package com.hc.type;

public enum ConfigTypeEnum {
    EQUIPMENT_TYPE(0),
    ARTIFACT_PROFILE(1),
    PROTOCOL(2);
    private int type;

    ConfigTypeEnum(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }
}

