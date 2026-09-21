package com.dataviz.file.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
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
     */
    @GetMapping("/download/{id}")
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
    public R<FileInfoVO> getFileInfo(@PathVariable Long id) {
        return R.ok(fileService.getFileInfo(id));
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return R.ok();
    }

    /**
     * 查询文件列表
     */
    @GetMapping("/list")
    public R<PageResult<FileInfoVO>> listFiles(FileQueryDTO queryDTO) {
        return R.ok(fileService.listFiles(queryDTO));
    }
}
