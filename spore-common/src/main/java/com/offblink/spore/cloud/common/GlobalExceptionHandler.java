package com.offblink.spore.cloud.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一异常出口：业务异常 → 对应 HTTP 状态 + R；其它 → 50000。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(ErrorCode.httpStatus(e.getCode()))
                .body(R.<Void>fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.status(500)
                .body(R.<Void>fail(ErrorCode.SERVER_ERROR, "服务内部错误"));
    }
}
