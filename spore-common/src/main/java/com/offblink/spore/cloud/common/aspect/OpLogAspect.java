package com.offblink.spore.cloud.common.aspect;

import com.offblink.spore.cloud.common.IdentityContext;
import com.offblink.spore.cloud.common.annotation.OpLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 操作日志切面：包住 {@link OpLog} 标注的方法，成功/失败都写一条 op_log。
 *
 * <p>日志写入失败只 warn、不影响业务（审计是旁路）；detail 截到 512 字符（列宽）。
 * 本类不带 {@code @Component}——common 不在各服务的扫描包里，由服务用 {@code @Bean} 显式注册。
 */
@Aspect
public class OpLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OpLogAspect.class);
    private static final int DETAIL_MAX = 512;

    private final JdbcTemplate jdbc;

    public OpLogAspect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object ret = pjp.proceed();
            write(opLog.value(), pjp, System.currentTimeMillis() - start, null);
            return ret;
        } catch (Throwable e) {
            write(opLog.value(), pjp, System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    private void write(String action, ProceedingJoinPoint pjp, long ms, Throwable err) {
        try {
            String detail = "dur=" + ms + "ms" + (err == null ? "" : " err=" + err.getMessage());
            if (detail.length() > DETAIL_MAX) {
                detail = detail.substring(0, DETAIL_MAX);
            }
            jdbc.update("INSERT INTO op_log(user_id, action, target, detail, ip) VALUES(?,?,?,?,?)",
                    IdentityContext.userId(), action, methodOf(pjp), detail, null);
        } catch (Exception e) {
            log.warn("op_log 写入失败 action={} : {}", action, e.toString());
        }
    }

    private static String methodOf(ProceedingJoinPoint pjp) {
        String target = pjp.getTarget() == null ? "?" : pjp.getTarget().getClass().getSimpleName();
        String name = pjp.getSignature() == null ? "?" : pjp.getSignature().getName();
        return target + "." + name;
    }
}
