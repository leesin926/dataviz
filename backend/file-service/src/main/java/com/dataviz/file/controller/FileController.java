package com.dataviz.file.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.file.dto.FileQueryDTO;
import com.dataviz.file.dto.FileUploadDTO;
import com.dataviz.file.service.FileService;
import com.dataviz.file.vo.FileInfoVO;
import com.dataviz.file.vo.FileUploadResultVO;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理控制器
 * <p>注解只挂在方法上，不挂类级：{@code /view/{id}} 是免登直读的图片地址（网关与
 * {@code AuthInterceptor} 白名单都列了它），类级读码会把这个方法一并拦住，大屏背景图当场变 403。
 * 代价是本类新增端点时不会"默认受保护"，得逐条想清楚它是公开面还是登录面。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    @RequiresPermission("file:write")
    public R<FileUploadResultVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizId", required = false) Long bizId,
            @RequestParam(value = "storageType", required = false) String storageType,
            @RequestParam(value = "bucketName", required = false) String bucketName) {
        FileUploadDTO uploadDTO = new FileUploadDTO();
        uploadDTO.setBizType(bizType);
        uploadDTO.setBizId(bizId);
        uploadDTO.setStorageType(storageType);
        uploadDTO.setBucketName(bucketName);
        FileUploadResultVO result = fileService.upload(file, uploadDTO);
        return R.ok(result);
    }

    /**
     * 批量上传文件
     */
    @PostMapping("/uploadBatch")
    @RequiresPermission("file:write")
    public R<List<FileUploadResultVO>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizId", required = false) Long bizId,
            @RequestParam(value = "storageType", required = false) String storageType,
            @RequestParam(value = "bucketName", required = false) String bucketName) {
        FileUploadDTO uploadDTO = new FileUploadDTO();
        uploadDTO.setBizType(bizType);
        uploadDTO.setBizId(bizId);
        uploadDTO.setStorageType(storageType);
        uploadDTO.setBucketName(bucketName);
        List<FileUploadResultVO> results = fileService.uploadBatch(files, uploadDTO);
        return R.ok(results);
    }

    /**
     * 下载文件
     * <p>本轮按"要登录"处理（{@code file:read}）。注意 {@code api-client/file.ts} 的注释说
     * 非 image/* 的资源走这条地址"免登直读"，而网关白名单里只有 /view —— 两处口径不一致。
     * 要么把 download 也公开（等于任何文件都能按 id 公网读），要么改前端注释与取用方式；
     * 这是产品决策，本轮没有替它选。</p>
     */
    @GetMapping("/download/{id}")
    @RequiresPermission("file:read")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        fileService.download(id, response);
    }

    /**
     * 图片直读：仅 image/*，浏览器 <img>/CSS 背景无法带 JWT，故该路径免登（见网关与 AuthInterceptor 白名单）
     */
    @GetMapping("/view/{id}")
    public void view(@PathVariable Long id, HttpServletResponse response) {
        fileService.view(id, response);
    }

    /**
     * 获取文件信息
     */
    @GetMapping("/info/{id}")
    @RequiresPermission("file:read")
    public R<FileInfoVO> getFileInfo(@PathVariable Long id) {
        return R.ok(fileService.getFileInfo(id));
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("file:write")
    public R<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return R.ok();
    }

    /**
     * 查询文件列表
     */
    @GetMapping("/list")
    @RequiresPermission("file:read")
    public R<PageResult<FileInfoVO>> listFiles(FileQueryDTO queryDTO) {
        return R.ok(fileService.listFiles(queryDTO));
    }
}
