package com.dataviz.user.service;

import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.common.security.util.LoginSessionCache;
import com.dataviz.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 授权/账号变更后驱逐登录态快照。
 * <p>
 * 各服务的 {@code AuthInterceptor} 判断"是否登录 + 有哪些权限"读的是 auth-service 在登录那一刻写进
 * Redis 的 {@code LoginUser}，而且剩余 TTL 不足 30 分钟就滑动续期 ⇒ 不驱逐的话，"改了权限""禁了账号"
 * 对在线用户**永远不生效**（不是延迟生效，是要等他主动退出登录）。权限强制一旦落地，这就是最难查的一类假象。
 * </p>
 * <p>
 * 调用点刻意放在事务方法内部、提交之前：万一事务回滚，代价是"相关用户多登录一次"，
 * 而不是"旧权限继续可用" —— 失败方向必须选前者。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSessionEvictor {

    private final UserMapper userMapper;
    private final CacheHelper cacheHelper;

    /** 单个用户（用户名从手上已有的实体取，别回头再查一次） */
    public void evictUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        try {
            LoginSessionCache.evict(cacheHelper, username);
        } catch (Exception e) {
            log.warn("驱逐登录态失败，授权变更需重新登录才生效: username={}, {}", username, e.getMessage());
        }
    }

    /** 某个角色下的全部成员：角色改授权、改名/停用、删除角色都走这里 */
    public void evictRoleMembers(Long roleId) {
        if (roleId == null) {
            return;
        }
        try {
            List<String> usernames = userMapper.selectUsernamesByRoleId(roleId);
            for (String username : usernames) {
                LoginSessionCache.evict(cacheHelper, username);
            }
            log.info("角色 {} 授权变更，已驱逐 {} 个在线会话快照", roleId, usernames.size());
        } catch (Exception e) {
            log.warn("按角色驱逐登录态失败: roleId={}, {}", roleId, e.getMessage());
        }
    }
}
