package com.mcode.gateway.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    Type model() default Type.REPEAT;

    long timeout() default 1000;

    /**
     * 幂等检查模式
     * repeat 过滤重复请求，重复请求会被过滤
     * limiting 限制接口请求频率，timeout内接口最多会被访问一次
     */
    enum Type {
        REPEAT,
        LIMITING;
    }
}
