package com.offblink.spore.cloud.common;

/** 业务错误码：前三位与 HTTP 状态对齐（与 Spore-GUI 同编码空间）。 */
public final class ErrorCode {

    public static final int OK = 0;
    public static final int BAD_REQUEST = 40001;
    public static final int UNAUTHORIZED = 40101;
    public static final int FORBIDDEN = 40301;
    public static final int NOT_FOUND = 40401;
    public static final int SERVER_ERROR = 50000;

    private ErrorCode() {
    }

    public static int httpStatus(int code) {
        switch (code) {
            case BAD_REQUEST:
                return 400;
            case UNAUTHORIZED:
                return 401;
            case FORBIDDEN:
                return 403;
            case NOT_FOUND:
                return 404;
            default:
                return 500;
        }
    }
}
