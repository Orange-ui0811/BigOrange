package com.orangejd.server.aspect;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.enumeration.OperationType;
import com.orangejd.server.annotation.AutoFill;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
public class AutoFillAspect {

    @Pointcut("@annotation(com.orangejd.server.annotation.AutoFill)")
    public void autoFillPointCut() {
    }

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) throws Exception {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0 || args[0] == null) {
            return;
        }

        Object entity = args[0];
        Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
        AutoFill autoFill = method.getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId() == null ? 0L : BaseContext.getCurrentId();

        invoke(entity, "setUpdateTime", LocalDateTime.class, now);
        invoke(entity, "setUpdateUser", Long.class, currentId);
        if (operationType == OperationType.INSERT) {
            invoke(entity, "setCreateTime", LocalDateTime.class, now);
            invoke(entity, "setCreateUser", Long.class, currentId);
        }
    }

    private void invoke(Object target, String methodName, Class<?> parameterType, Object value) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            method.invoke(target, value);
        } catch (Exception ignored) {
        }
    }
}

