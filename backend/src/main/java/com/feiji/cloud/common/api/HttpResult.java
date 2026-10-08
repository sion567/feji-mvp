package com.feiji.cloud.common.api;

public class HttpResult<T> {

    private int code;
    private String message;
    private T data;

    public static <T> HttpResult<T> ok() {
        return ok(null);
    }

    public static <T> HttpResult<T> ok(T data) {
        HttpResult<T> result = new HttpResult<>();
        result.code = ResultCode.OK.getCode();
        result.message = ResultCode.OK.getMessage();
        result.data = data;
        return result;
    }

    public static <T> HttpResult<T> error(ResultCode resultCode) {
        return error(resultCode.getCode(), resultCode.getMessage());
    }

    public static <T> HttpResult<T> error(int code, String message) {
        HttpResult<T> result = new HttpResult<>();
        result.code = code;
        result.message = message;
        return result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
