package com.offblink.spore.cloud.common;

/** 业务异常：全局处理器转成 {@link R} + 对应 HTTP 状态。 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
