package com.offblink.spore.cloud.common;

/**
 * 统一返回结构 {@code {code,message,data}}（与 Spore-GUI / 手机端 SyncClient 完全一致）。
 * code=0 成功，非 0 见 {@link ErrorCode}；HTTP 状态由 {@link ErrorCode#httpStatus} 决定。
 *
 * <p>手机端 {@code SyncClient} 只认这一层包装：任何服务（含网关自身的 401）
 * 都必须吐这段 JSON，否则客户端会报「响应不是 R 包装」。
 */
public class R<T> {

    private int code;
    private String message;
    private T data;

    public R() {
    }

    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        return new R<T>(ErrorCode.OK, "ok", data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<T>(code, message, null);
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
