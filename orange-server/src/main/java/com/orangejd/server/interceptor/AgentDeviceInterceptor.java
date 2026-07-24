package com.orangejd.server.interceptor;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.service.AgentDeviceAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class AgentDeviceInterceptor implements HandlerInterceptor {

    public static final String CREDENTIAL_ID_ATTRIBUTE = "orangejd.agentCredentialId";
    public static final String SCOPES_ATTRIBUTE = "orangejd.agentScopes";
    private final AgentDeviceAuthService authService;

    public AgentDeviceInterceptor(AgentDeviceAuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        try {
            Map<String, Object> credential = authService.authenticate(request.getHeader("Authorization"));
            BaseContext.setCurrentId(((Number) credential.get("accountId")).longValue());
            request.setAttribute(CREDENTIAL_ID_ATTRIBUTE,
                    ((Number) credential.get("id")).longValue());
            request.setAttribute(SCOPES_ATTRIBUTE, credential.get("scopes"));
            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.clear();
    }
}
