package com.dataviz.schedule.job;

import com.dataviz.common.core.exception.BizException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注册表是"能被调度的东西"的唯一闭集，所以它自己必须先把重复键和空键挡住：
 * 两个处理器抢同一个键时，后扫描到的那个会静默覆盖前一个，症状是"同一批任务今天跑 A 明天跑 B"。
 */
class JobRegistryTest {

    /** 测试用的处理器：只关心键与名，不碰数据库 */
    private static ScheduledJob handler(final String key, final String label) {
        return new ScheduledJob() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public String description() {
                return label + " 的描述";
            }

            @Override
            public void execute(String params) {
                // 什么都不做：这里测的是注册表，不是处理器
            }
        };
    }

    private static JobRegistry registryOf(List<ScheduledJob> handlers) {
        JobRegistry registry = new JobRegistry(handlers);
        registry.index();
        return registry;
    }

    @Test
    @DisplayName("require 命中时返回对应处理器，键名两侧空格不算另一个键")
    void should_returnHandler_when_keyRegistered() {
        JobRegistry registry = registryOf(Collections.singletonList(handler("jobLogCleanup", "清理日志")));

        assertThat(registry.require("jobLogCleanup").label()).isEqualTo("清理日志");
        assertThat(registry.contains(" jobLogCleanup ")).isTrue();
    }

    @Test
    @DisplayName("require 未命中时报错要把可选项带上，而不是只说参数错误")
    void should_listAvailableKeys_when_keyUnknown() {
        final JobRegistry registry = registryOf(Arrays.asList(handler("a", "A"), handler("b", "B")));

        assertThatThrownBy(() -> registry.require("Ghost"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未注册的任务处理器: Ghost")
                .hasMessageContaining("a, b");
    }

    @Test
    @DisplayName("空 jobClass 同样报 400：一个不执行任何东西的任务不该能落库")
    void should_reject_when_keyBlank() {
        final JobRegistry registry = registryOf(Collections.singletonList(handler("a", "A")));

        assertThatThrownBy(() -> registry.require("   "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("任务处理器不能为空");
    }

    @Test
    @DisplayName("重复键在启动时就炸，不允许后注册的静默覆盖前一个")
    void should_failFast_when_duplicateKey() {
        final List<ScheduledJob> duplicated =
                Arrays.asList(handler("same", "第一个"), handler("same", "第二个"));

        assertThatThrownBy(() -> registryOf(duplicated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("键名重复");
    }

    @Test
    @DisplayName("available 保序，前端下拉的次序不会每次重启换一个样子")
    void should_keepOrder_when_listingAvailable() {
        JobRegistry registry = registryOf(Arrays.asList(handler("z", "Z"), handler("y", "Y")));

        assertThat(registry.available()).hasSize(2);
        assertThat(registry.available().get(0).key()).isEqualTo("z");
        assertThat(registry.find("nope")).isNull();
    }
}
