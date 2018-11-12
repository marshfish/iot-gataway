package com.hc.rpc;

import lombok.Data;

import java.util.Map;

@Data
public class PublishEvent {
    private String downQueueName;
    private byte[] message;
    private Map<String, Object> headers;

    public PublishEvent(String downQueueName, byte[] message, Map<String, Object> headers) {
        this.downQueueName = downQueueName;
        this.message = message;
        this.headers = headers;
    }
}
