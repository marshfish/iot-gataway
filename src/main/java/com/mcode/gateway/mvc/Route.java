package com.mcode.gateway.mvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Route {
    /**
     * uri
     */
    String value();

    /**
     * method
     */
    HttpMethod method() default HttpMethod.GET;
    /**
     * description
     */
    String desc() default "";
}
