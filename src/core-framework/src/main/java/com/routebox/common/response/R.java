package com.routebox.common.response;

import lombok.Data;

/**
 * @Description Unified Response Entity
 * @Date 2025/7/21
**/
@Data
public class R<T> {
    private int code;
    private String mes;
    private T data;

    private R() {
    }

    /**
     * @Description Success with default message
     * @Date 2025/7/21
     **/
    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMes("success");

        return r;
    }

    /**
     * @Description Error with default message
     * @Date 2025/7/21
     **/
    public static <T> R<T> error() {
        R<T> r = new R<>();
        r.setCode(400);
        r.setMes("fail");

        return r;
    }

    /**
     * @Description Error with custom message
     * @Date 2025/7/21
     **/
    public static <T> R<T> error(String mes) {
        R<T> r = new R<>();
        r.setCode(500);
        r.setMes(mes);

        return r;
    }
    /**
     * @Description Error with custom message and data
     * @Date 2025/7/21
     **/
    public static <T> R<T> error(String mes, T data) {
        R<T> r = new R<>();
        r.setCode(500);
        r.setMes(mes);
        r.setData(data);

        return r;
    }
    /**
     * @Description Success with custom message and data
     * @Date 2025/7/21
     **/
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMes("success");
        r.setData(data);

        return r;
    }

    /**
     * @Description Success with custom message and data
     * @Date 2025/7/21
     **/
    public static <T> R<T> ok(String mes, T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMes(mes);
        r.setData(data);

        return r;
    }
}

