package com.hc.type;

public enum ProfileEnum {
    DEV(0, "dec"),
    TEST(1, "test"),
    PREONLINE(2, "profile"),
    ONLINE(3, "online");

    private int profile;
    private String desc;

    ProfileEnum(int profile, String desc) {
        this.profile = profile;
        this.desc = desc;
    }

    public int getProfile() {
        return profile;
    }

    public String getDesc() {
        return desc;
    }
}
