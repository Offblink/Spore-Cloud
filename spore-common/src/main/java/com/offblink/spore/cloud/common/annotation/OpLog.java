package com.offblink.spore.cloud.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 操作日志（AOP 切面）：记 action / 调用者 / 耗时 / 异常，落 op_log 表。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 动作码，如 user.login / sync.push */
    String value();
}
