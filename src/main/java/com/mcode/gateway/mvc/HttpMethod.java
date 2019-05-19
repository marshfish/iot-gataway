package com.mcode.gateway.mvc;

public enum HttpMethod {
    GET("GET"), POST("POST"), DELETE("DELETE"), PUT("PUT");
    private String value;

    HttpMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
