package com.dataviz.alert.engine;

import com.dataviz.alert.service.impl.AlertCheckServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 BE1（API-111）的看门测试。
 * <p>
 * 被退场的那份 {@code engine/AlertCheckEngine} 用 {@code Math.random()*100} 与硬编码阈值 80.0 判告警，
 * 带一个<b>不受任何开关控制</b>的 {@code @Scheduled(fixedDelay = 60000)}，每轮都把随机数越界的事件
 * 写进 {@code alert_event} 并调 {@code NotifyDispatcher} 真发通知（归档见
 * {@code backend/_archive/alert-fake-engine-20261106/README.md}）。
 * 这里钉三件事：那个类不在了、真服务里只剩一处定时入口、那一处是带开关且默认关的。
 */
class AlertCheckEngineRetirementTest {

    @Test
    @DisplayName("假引擎已退场 - 类路径里不该再有 AlertCheckEngine")
    void should_notBeLoadable_when_fakeEngineRetired() {
        ClassNotFoundException e = assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.dataviz.alert.engine.AlertCheckEngine"));
        assertTrue(e.getMessage().endsWith("AlertCheckEngine"), e.getMessage());
    }

    @Test
    @DisplayName("alert-service 的定时入口只剩真巡检这一个方法")
    void should_haveExactlyOneScheduledMethod_when_scanAlertBeans() {
        List<String> scheduled = scheduledMethodsOfAlertBeans();

        assertEquals(Collections.singletonList(
                AlertCheckServiceImpl.class.getName() + "#checkAllRules"), scheduled);
    }

    @Test
    @DisplayName("唯一的定时入口由 alert.check.enabled 门控且默认关闭")
    void should_beGatedAndOffByDefault_when_onlyScheduler() {
        Field gate = gatedFieldOf(AlertCheckServiceImpl.class);

        assertTrue(gate.getType() == boolean.class || gate.getType() == Boolean.class,
                "门控字段该是布尔：" + gate);
        String expression = gate.getAnnotation(Value.class).value();
        assertTrue(expression.contains("alert.check.enabled"), expression);
        // 默认值必须是 false：打开后每 60s 真查数据源并真发通知，不能是"克隆下来就在发信"
        assertTrue(expression.endsWith(":false}"), "开关默认值不是关：" + expression);
    }

    /** 扫 com.dataviz.alert 下的 Spring bean（@Component 及其派生注解），收集带 @Scheduled 的 类#方法 */
    private List<String> scheduledMethodsOfAlertBeans() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<String> found = new ArrayList<>();
        scanner.findCandidateComponents("com.dataviz.alert").forEach(candidate -> {
            Class<?> clazz;
            try {
                clazz = Class.forName(candidate.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("扫描到但加载不了：" + candidate.getBeanClassName(), e);
            }
            ReflectionUtils.doWithMethods(clazz, method -> {
                if (method.isAnnotationPresent(Scheduled.class)) {
                    found.add(clazz.getName() + "#" + method.getName());
                }
            });
        });
        Collections.sort(found);
        return found;
    }

    private Field gatedFieldOf(Class<?> clazz) {
        List<Field> gates = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            Value annotation = field.getAnnotation(Value.class);
            if (annotation != null && annotation.value().contains("alert.check.enabled")) {
                gates.add(field);
            }
        }
        assertEquals(1, gates.size(), () -> clazz.getName() + " 上带告警开关的字段应有且只有一个，实际 " + gates);
        return gates.get(0);
    }

    /** 防止有人把 @Scheduled 挪到别的类上而不留痕迹：注解本身也可数一遍 */
    @Test
    @DisplayName("真巡检方法确实带 60s 周期注解")
    void should_stillBeScheduled_when_realCheckerInspected() {
        int count = 0;
        Method chosen = null;
        for (Method method : AlertCheckServiceImpl.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Scheduled.class)) {
                count++;
                chosen = method;
            }
        }
        assertEquals(1, count);
        assertEquals("checkAllRules", chosen.getName());
        assertEquals(60000L, chosen.getAnnotation(Scheduled.class).fixedDelay());
    }
}
