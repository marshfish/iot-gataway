package com.hc.type;

public enum ProtocolEnum {
    TCP(0, "tcp"),
    UDP(1, "udp"),
    MQTT(2, "mqtt");
    private int type;
    private String desc;

    ProtocolEnum(int type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public int getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }
}
