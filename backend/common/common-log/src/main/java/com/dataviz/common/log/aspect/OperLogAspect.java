package com.dataviz.common.log.aspect;

import com.dataviz.common.core.util.JsonUtils;
import com.dataviz.common.log.annotation.OperLog;
import com.dataviz.common.log.event.OperLogEvent;
import com.dataviz.common.log.util.LogUtils;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志切面
 */
@Slf4j
@Aspect
@Component
public class OperLogAspect {

    private final ApplicationEventPublisher eventPublisher;

    public OperLogAspect(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        OperLogEvent logEvent = buildLogEvent(joinPoint, operLog);

        Object result = null;
        try {
            result = joinPoint.proceed();
            logEvent.setStatus(0); // 成功

            if (operLog.recordResult() && result != null) {
                logEvent.setResponseResult(truncate(JsonUtils.toJson(result), 2000));
            }
        } catch (Throwable e) {
            logEvent.setStatus(1); // 失败
            logEvent.setErrorMsg(truncate(e.getMessage(), 2000));
            throw e;
        } finally {
            logEvent.setCostTime(System.currentTimeMillis() - startTime);
            logEvent.setOperTime(LocalDateTime.now());

            // 发布事件，异步处理日志持久化
            try {
                eventPublisher.publishEvent(logEvent);
            } catch (Exception e) {
                log.error("发布操作日志事件失败", e);
            }

            log.info("操作日志: {} - {} [{}], 耗时: {}ms",
                    logEvent.getOperType(), logEvent.getDescription(),
                    logEvent.getStatus() == 0 ? "成功" : "失败", logEvent.getCostTime());
        }

        return result;
    }

    /**
     * 构建日志事件
     */
    private OperLogEvent buildLogEvent(ProceedingJoinPoint joinPoint, OperLog operLog) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        HttpServletRequest request = getRequest();

        OperLogEvent event = new OperLogEvent();
        event.setModule(operLog.module());
        event.setOperType(operLog.type().getDescription());
        event.setDescription(operLog.description());
        event.setMethod(joinPoint.getTarget().getClass().getName() + "." + method.getName());

        if (request != null) {
            event.setRequestUrl(request.getRequestURI());
            event.setHttpMethod(request.getMethod());
            event.setOperIp(LogUtils.getClientIp(request));
        }

        // 记录请求参数
        if (operLog.recordParams()) {
            String params = getRequestParams(joinPoint, signature);
            event.setRequestParams(truncate(params, 2000));
        }

        // 设置操作人信息
        LoginUser loginUser = SecurityContextHolder.getLoginUser();
        if (loginUser != null) {
            event.setOperUserId(loginUser.getUserId());
            event.setOperUsername(loginUser.getUsername());
            event.setOperNickname(loginUser.getNickname());
            event.setTenantId(loginUser.getTenantId());
        }

        return event;
    }

    /**
     * 获取请求参数
     */
    private String getRequestParams(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        try {
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            if (paramNames == null || args == null || args.length == 0) {
                return "";
            }

            Map<String, Object> paramMap = new LinkedHashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                Object arg = args[i];
                // 跳过无法序列化的参数
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                        || arg instanceof MultipartFile) {
                    continue;
                }
                paramMap.put(paramNames[i], arg);
            }
            return JsonUtils.toJson(paramMap);
        } catch (Exception e) {
            log.warn("获取请求参数失败", e);
            return "参数获取失败";
        }
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
