package com.hc.type;

public enum EventTypeEnum {
    CONNECTOR_RESPONSE(0, "connector_response"),
    DEVICE_LOGIN(1, "login"),
    DEVICE_LOGOUT(2, "logout"),
    DEVICE_UPLOAD(3, "data_upload"),
    LOGIN_SUCCESS(4, "login_success"),
    LOGIN_FAIL(5, "login_fail"),
    SERVER_PUBLISH(6, "server_publish"),
    CONFIG_DISCOVER(7, "config_discover"),
    INSTANCE_REGISTER(8, "instance_register"),
    REGISTER_SUCCESS(9, "register_success"),
    REGISTER_FAIL(10, "register_fail"),
    PING(11, "ping"),
    PONG(12, "pong");
    private int type;
    private String desc;

    EventTypeEnum(int type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public int getType() {
        return type;
    }

    public static EventTypeEnum getEnumByCode(int code) {
        for (EventTypeEnum eventTypeEnum : EventTypeEnum.values()) {
            if (code == eventTypeEnum.type) {
                return eventTypeEnum;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "EventTypeEnum{" +
                "type=" + type +
                ", desc='" + desc + '\'' +
                '}';
    }
}
