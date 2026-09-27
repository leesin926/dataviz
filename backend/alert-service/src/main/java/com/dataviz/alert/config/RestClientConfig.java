package com.dataviz.alert.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

@Configuration
public class RestClientConfig {

    /**
     * 通知发送是另一个量级的风险：外部 webhook 一挂就会把整轮规则检查堵住，
     * 所以给它一个短超时实例。指标取数用的长超时实例在 common-core 的
     * {@code DatasourceQueryClient} 内部自建（见 D45），这里不再重复声明。
     * <p>
     * {@code setInstanceFollowRedirects(false)} 是<b>加固，不是修缺陷</b>，而且必须写在 {@code super}
     * 之后 —— 两条都是探针实测出来的（报告 19.10 B 组）：
     * <ul>
     *   <li>父类 {@code SimpleClientHttpRequestFactory.prepareConnection} 自己按方法设这项：GET 设
     *       {@code true}、其他设 {@code false}。写在 {@code super} 之前会被原样覆盖（第一版就是这么错的，
     *       现象是"改了跟没改一模一样"）；</li>
     *   <li>也因此，通知器用的 POST 本来就不跟随重定向 ⇒ "公网域名 302 把服务端送进 127.0.0.1" 这条洞
     *       当下不可达。这一行防的是将来换 factory（HttpComponents 会跟 POST 的 307/308）、或有人开
     *       {@code -Dhttp.strictPostRedirect=true} 时绕开 {@code OutboundUrlGuard}（它只看过原始那个 URL）。
     *       GET 方向则是实打实的行为变化：不写这行，GET 会跟着 302 进到内网去。</li>
     * </ul>
     * 顺带符合通知语义：重定向回来的响应没有可读的 errcode，本来就该记 FAILED 而不是记成功。
     */
    @Bean("notifyRestTemplate")
    public RestTemplate notifyRestTemplate(RestTemplateBuilder builder) {
        return builder.requestFactory(NotifyRequestFactory::new).build();
    }

    private static final class NotifyRequestFactory extends SimpleClientHttpRequestFactory {

        private NotifyRequestFactory() {
            setConnectTimeout(3000);
            setReadTimeout(10000);
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            // 必须在 super 之后：父类会按 httpMethod 把这一项设成 true(GET) / false(其他)
            connection.setInstanceFollowRedirects(false);
        }
    }
}
