package com.offblink.spore.cloud.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常出口：业务异常 → 对应 HTTP 状态 + R；参数校验 → 40001；其它 → 50000。
 *
 * <p>本类在 {@code com.offblink.spore.cloud.common} 包里，不在各服务的扫描范围，
 * 由各服务 {@code @Import(GlobalExceptionHandler.class)} 引入。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(ErrorCode.httpStatus(e.getCode()))
                .body(R.<Void>fail(e.getCode(), e.getMessage()));
    }

    /** @Valid 打在 @RequestBody 上：字段级校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleInvalidBody(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldError();
        String msg = first == null
                ? "请求参数校验失败"
                : first.getField() + " " + first.getDefaultMessage();
        return ResponseEntity.status(400).body(R.<Void>fail(ErrorCode.BAD_REQUEST, msg));
    }

    /** 方法参数级校验（@Validated + @RequestParam 约束） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleViolation(ConstraintViolationException e) {
        return ResponseEntity.status(400)
                .body(R.<Void>fail(ErrorCode.BAD_REQUEST, "请求参数校验失败: " + e.getMessage()));
    }

    /** 请求体不是合法 JSON（手机端推坏包时给明确 40001，而不是 500） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(400)
                .body(R.<Void>fail(ErrorCode.BAD_REQUEST, "请求体不是合法 JSON"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.status(500)
                .body(R.<Void>fail(ErrorCode.SERVER_ERROR, "服务内部错误"));
    }
}
