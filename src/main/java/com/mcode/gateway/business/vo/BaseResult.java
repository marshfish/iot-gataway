package com.mcode.gateway.business.vo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class BaseResult {
    private int code = 1;
    private String tip = "success";
    private Object data;

    public BaseResult(int code, String tip) {
        this.code = code;
        this.tip = tip;
    }

    public BaseResult(Object data) {
        this.data = data;
    }

    public static BaseResult getInstance() {
        return SingletonResult.baseResult;
    }


    private static class SingletonResult {
        private static final BaseResult baseResult = new BaseResult(null);
    }
}
