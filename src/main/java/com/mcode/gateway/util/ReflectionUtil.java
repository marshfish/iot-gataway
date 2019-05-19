package com.mcode.gateway.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Title:    FluxMVC
 * Description:
 *
 * @author kaibo
 * @version 1.0
 * @Ddate 2018/1/6
 */
@Slf4j
public final class ReflectionUtil {

    /**
     * 创建实例
     */
    public static <T> T newInstance(Class<T> cls) {
        T obj = null;
        try {
            obj = cls.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return obj;
    }

    /**
     * 调用方法
     */
    public static Object invokeMethod(Object obj, Method method, Object... args) {
        method.setAccessible(true);
        Object result;
        try {
            result = method.invoke(obj, args);
        } catch (InvocationTargetException e) {
            throw (RuntimeException) e.getTargetException();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("反射调用权限异常");
        }
        return result;
    }

    /**
     * 设置成员变量的值
     */
    public static void setField(Object obj, Field field, Object value) {
        field.setAccessible(true);
        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

}
