package com.orangejd.server.aspect;

import com.orangejd.common.context.BaseContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Aspect
public class AdminAuditAspect {

    private static final Logger log = LoggerFactory.getLogger("ADMIN_AUDIT");

    @Pointcut("execution(* com.orangejd.server.controller.admin..*.*(..))")
    public void adminController() {}

    /** 只拦截增删改操作 */
    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
              "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
              "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void writeOps() {}

    @Before("adminController() && writeOps()")
    public void auditWrite(JoinPoint jp) {
        try {
            Long adminId = BaseContext.getCurrentId();
            String action = jp.getSignature().getDeclaringType().getSimpleName() + "."
                    + jp.getSignature().getName();
            String ip = "unknown";
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                ip = req.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) ip = req.getRemoteAddr();
            }
            log.info("adminId={} action={} ip={} args={}",
                    adminId != null ? adminId : 0, action, ip, jp.getArgs());
        } catch (Exception ignored) {}
    }
}
