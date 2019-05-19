package com.mcode.gateway.mvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅支持rest风格HTTP请求：application/json + HTTP Method（get/post/put/delete）
 */
@Target(value = ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestManager {
    /**
     * uri
     */
    String value() default "";
}
