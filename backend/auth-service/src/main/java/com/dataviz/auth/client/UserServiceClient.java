package com.dataviz.auth.client;

import com.dataviz.auth.client.dto.AuthUserDTO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * user-service 内部认证接口客户端。
 * <p>
 * RBAC 数据（用户/角色/权限）唯一归属 db_user，auth-service 不再直连这些表，
 * 因此这里区分两类结果：<b>用户不存在</b>返回 null（HTTP 200 + data=null），
 * <b>调用失败</b>抛 503 —— 不能把库故障或口令不一致伪装成"用户名或密码错误"。
 * </p>
 */
@Slf4j
@Component
public class UserServiceClient {

    private static final String PREFIX = "/user/internal/auth-user";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;

    public UserServiceClient(RestTemplate restTemplate,
                            @Value("${user-service.base-url}") String baseUrl,
                            @Value("${internal.api.token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalToken = internalToken;
    }

    /** @return null 表示用户不存在 */
    public AuthUserDTO findByUsername(String username) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + PREFIX)
                .queryParam("username", username)
                .build().encode().toUri();
        return call(HttpMethod.GET, uri, null, new ParameterizedTypeReference<R<AuthUserDTO>>() { }, "查询用户 " + username);
    }

    /** @return null 表示用户不存在 */
    public AuthUserDTO findById(Long userId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + PREFIX + "/" + userId).build().toUri();
        return call(HttpMethod.GET, uri, null, new ParameterizedTypeReference<R<AuthUserDTO>>() { }, "查询用户 id=" + userId);
    }

    public List<String> findRoleCodes(Long userId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + PREFIX + "/" + userId + "/role-codes").build().toUri();
        List<String> codes = call(HttpMethod.GET, uri, null, new ParameterizedTypeReference<R<List<String>>>() { }, "查询角色码");
        return codes == null ? java.util.Collections.<String>emptyList() : codes;
    }

    public List<String> findPermissionCodes(Long userId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + PREFIX + "/" + userId + "/permission-codes").build().toUri();
        List<String> codes = call(HttpMethod.GET, uri, null, new ParameterizedTypeReference<R<List<String>>>() { }, "查询权限码");
        return codes == null ? java.util.Collections.<String>emptyList() : codes;
    }

    public void recordLoginInfo(Long userId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + PREFIX + "/" + userId + "/login-info").build().toUri();
        call(HttpMethod.PUT, uri, null, new ParameterizedTypeReference<R<Void>>() { }, "回写登录时间");
    }

    private <T> T call(HttpMethod method, URI uri, Object body, ParameterizedTypeReference<R<T>> type, String what) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        try {
            ResponseEntity<R<T>> response = restTemplate.exchange(uri, method, new HttpEntity<Object>(body, headers), type);
            R<T> envelope = response.getBody();
            if (envelope == null) {
                throw unavailable(what + "：用户服务返回空响应体");
            }
            if (!envelope.isSuccess()) {
                throw unavailable(what + "：用户服务返回 code=" + envelope.getCode());
            }
            return envelope.getData();
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getRawStatusCode() == HttpStatus.FORBIDDEN.value()) {
                log.error("内部口令被用户服务拒绝: {} {} —— 请核对两边 internal.api.token 是否一致", method, uri);
                throw unavailable(what + "：内部口令不被用户服务接受");
            }
            log.error("调用用户服务失败: {} {} status={}", method, uri, e.getRawStatusCode(), e);
            throw unavailable(what + "：用户服务返回 HTTP " + e.getRawStatusCode());
        } catch (RestClientException e) {
            log.error("无法访问用户服务: {} {} cause={}", method, uri, e.getMessage());
            throw unavailable(what + "：无法访问用户服务");
        }
    }

    private BizException unavailable(String message) {
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
