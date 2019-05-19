package com.mcode.gateway.type;

public enum QosType {
    AT_MOST_ONCE(0), AT_LEAST_ONCE(1);
    private int type;

    QosType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }
}
