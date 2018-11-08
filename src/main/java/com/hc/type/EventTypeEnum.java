package com.hc.type;

public enum EventTypeEnum {
    DEVICE_LOGIN(1, "login"),
    DEVICE_LOGOUT(2, "logout"),
    DEVICE_UPLOAD(3, "data_upload"),
    LOGIN_SUCCESS(4, "login_success"),
    LOGIN_FAIL(5, "login_fail"),
    SERVER_PUBLISH(6, "server_publish"),
    CLIENT_RESPONSE(7,"client_response"),
    INSTANCE_REGISTER(9, "instance_register"),
    REGISTER_SUCCESS(10, "register_success"),
    REGISTER_FAIL(11, "register_fail"),
    PING(12, "ping"),
    PONG(13, "pong"),
    DROPPED(14,"dropped");
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
