package com.dataviz.admin.controller;

import com.dataviz.admin.service.AuditLogService;
import com.dataviz.admin.vo.AuditLogVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
// 类级 = 本控制器全部端点至少要 platform:read（PermissionInterceptor 会回落到类上的注解），本控制器纯读（分页/详情/导出），无 write 端点。
@RequiresPermission("platform:read")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/page")
    public R<PageResult<AuditLogVO>> list(PageQuery pageQuery,
                                          @RequestParam(required = false) String username,
                                          @RequestParam(required = false) String module,
                                          @RequestParam(required = false) String action) {
        return R.ok(auditLogService.getLogs(pageQuery, username, module, action));
    }

    @GetMapping("/{id}")
    public R<AuditLogVO> getDetail(@PathVariable Long id) {
        return R.ok(auditLogService.getDetail(id));
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String username,
                       @RequestParam(required = false) String module,
                       @RequestParam(required = false) String action) {
        auditLogService.exportToExcel(response, username, module, action);
    }
}
