package com.dataviz.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.file.dto.FileQueryDTO;
import com.dataviz.file.dto.FileUploadDTO;
import com.dataviz.file.entity.FileInfo;
import com.dataviz.file.mapper.FileMapper;
import com.dataviz.file.service.FileService;
import com.dataviz.file.service.strategy.StorageStrategy;
import com.dataviz.file.service.strategy.StorageStrategyRouter;
import com.dataviz.file.vo.FileInfoVO;
import com.dataviz.file.vo.FileUploadResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 浏览器无法为 <img>/CSS 背景携带 JWT，故图片走免登的 /view 端点，且只放行图片类型 */
    private static final String VIEW_URL_PREFIX = "/api/file/view/";
    private static final String DOWNLOAD_URL_PREFIX = "/api/file/download/";

    private final FileMapper fileMapper;
    private final StorageStrategyRouter storageStrategyRouter;

    @Value("${minio.bucket:dataviz}")
    private String bucket;

    public FileServiceImpl(FileMapper fileMapper, StorageStrategyRouter storageStrategyRouter) {
        this.fileMapper = fileMapper;
        this.storageStrategyRouter = storageStrategyRouter;
    }

    @Override
    @Transactional
    public FileUploadResultVO upload(MultipartFile file, FileUploadDTO uploadDTO) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        // 写侧策略由配置选定（file.storage.type），并在选定阶段完成 minio 必填项校验；
        // 校验不过会在这里抛出，落库与策略写入都还没发生，不会留下"记了 local 其实没存"的假行。
        StorageStrategyRouter.WriteRoute route = storageStrategyRouter.routeForWrite();
        String originalFilename = file.getOriginalFilename();
        String storedName = buildObjectName(originalFilename);
        String bk = (uploadDTO != null && uploadDTO.getBucketName() != null && !uploadDTO.getBucketName().isEmpty())
                ? uploadDTO.getBucketName() : bucket;

        route.getStrategy().upload(file, bk, storedName);

        FileInfo fileInfo = FileInfo.builder()
                .fileName(originalFilename)
                .filePath(storedName)
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .bucket(bk)
                // 落"真正处理过它的那个策略"，而不是当前配置值：日后切配置，老行仍知道自己在哪
                .storageType(route.getStorageType())
                .createTime(LocalDateTime.now())
                .build();
        fileMapper.insert(fileInfo);

        log.info("Uploaded file: id={}, name={}, path={}, storageType={}",
                fileInfo.getId(), originalFilename, storedName, route.getStorageType());

        return FileUploadResultVO.builder()
                .id(fileInfo.getId())
                .originalName(originalFilename)
                .storedName(storedName)
                .fileUrl(urlOf(fileInfo))
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .storageType(route.getStorageType())
                .createTime(fileInfo.getCreateTime())
                .build();
    }

    @Override
    @Transactional
    public List<FileUploadResultVO> uploadBatch(List<MultipartFile> files, FileUploadDTO uploadDTO) {
        List<FileUploadResultVO> results = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            results.add(upload(file, uploadDTO));
        }
        return results;
    }

    @Override
    public void download(Long id, HttpServletResponse response) {
        FileInfo fileInfo = requireFile(id);
        writeTo(response, fileInfo, true);
    }

    @Override
    public void view(Long id, HttpServletResponse response) {
        FileInfo fileInfo = requireFile(id);
        if (fileInfo.getFileType() == null || !fileInfo.getFileType().startsWith("image/")) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        writeTo(response, fileInfo, false);
    }

    private String urlOf(FileInfo fileInfo) {
        return isImage(fileInfo.getFileType()) ? VIEW_URL_PREFIX + fileInfo.getId()
                : DOWNLOAD_URL_PREFIX + fileInfo.getId();
    }

    @Override
    public FileInfoVO getFileInfo(Long id) {
        FileInfo fileInfo = requireFile(id);
        FileInfoVO vo = new FileInfoVO();
        BeanUtils.copyProperties(fileInfo, vo);
        return vo;
    }

    @Override
    @Transactional
    public void deleteFile(Long id) {
        FileInfo fileInfo = requireFile(id);
        // 按行记录的 storage_type 去删：切了配置也不该拿当前策略去删一个不属于自己的对象。
        // 路由不过就地拒绝，DB 行保留（宁可留一条看得见的不一致，也不能悄悄删掉指针、留对象在盘上失联）
        StorageStrategy strategy = storageStrategyRouter.routeForRead(fileInfo.getStorageType());
        strategy.delete(fileInfo.getBucket(), fileInfo.getFilePath());
        fileMapper.deleteById(id);
        log.info("Deleted file: id={}, path={}, storageType={}",
                id, fileInfo.getFilePath(), fileInfo.getStorageType());
    }

    @Override
    public PageResult<FileInfoVO> listFiles(FileQueryDTO queryDTO) {
        int pageNum = queryDTO.getPageNum();
        int pageSize = queryDTO.getPageSize();
        Page<FileInfo> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().trim().isEmpty()) {
            wrapper.like(FileInfo::getFileName, queryDTO.getKeyword());
        }
        if (queryDTO.getBizType() != null && !queryDTO.getBizType().trim().isEmpty()) {
            wrapper.eq(FileInfo::getFileType, queryDTO.getBizType());
        }
        if (queryDTO.getUploaderId() != null) {
            wrapper.eq(FileInfo::getCreateBy, queryDTO.getUploaderId());
        }
        wrapper.orderByDesc(FileInfo::getCreateTime);
        Page<FileInfo> result = fileMapper.selectPage(page, wrapper);
        List<FileInfoVO> voList = result.getRecords().stream().map(f -> {
            FileInfoVO vo = new FileInfoVO();
            BeanUtils.copyProperties(f, vo);
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(voList, result.getTotal(), pageNum, pageSize);
    }

    private FileInfo requireFile(Long id) {
        FileInfo fileInfo = fileMapper.selectById(id);
        if (fileInfo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "File not found: " + id);
        }
        return fileInfo;
    }

    private void writeTo(HttpServletResponse response, FileInfo fileInfo, boolean asAttachment) {
        // 读侧路由放在 try 之外：拒绝（类型缺失/未接入）是"路由不成立"，
        // 若塞进 try 会被下面的兜底改写成语义错误的 404 "File content unavailable"
        StorageStrategy strategy = storageStrategyRouter.routeForRead(fileInfo.getStorageType());
        try (InputStream in = strategy.download(fileInfo.getBucket(), fileInfo.getFilePath())) {
            String contentType = fileInfo.getFileType() == null ? "application/octet-stream" : fileInfo.getFileType();
            response.setContentType(contentType);
            response.setContentLengthLong(fileInfo.getFileSize() == null ? -1 : fileInfo.getFileSize());
            if (asAttachment) {
                response.setHeader("Content-Disposition", attachmentHeader(fileInfo.getFileName()));
            } else {
                response.setHeader("Content-Disposition", "inline");
                response.setHeader("Cache-Control", "public, max-age=86400");
            }
            OutputStream os = response.getOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            os.flush();
            log.info("{} file: id={}, name={}", asAttachment ? "Downloading" : "Viewing",
                    fileInfo.getId(), fileInfo.getFileName());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 库里有记录但盘上对象缺失（换工作目录启动、手工清盘、只灌库未上传都会这样）：
            // 这是"资源不存在"，报 404 才不会被误判成上传链路故障。
            // /view 是免登公开端点，响应里只给 id，内部存储路径与原文件名只进日志。
            log.error("Failed to read file {}: {}", fileInfo.getId(), e.getMessage(), e);
            throw new BizException(ErrorCode.NOT_FOUND, "File content unavailable: " + fileInfo.getId());
        }
    }

    private String attachmentHeader(String fileName) {
        if (fileName == null) {
            return "attachment";
        }
        try {
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            return "attachment;filename=" + encoded + ";filename*=UTF-8''" + encoded;
        } catch (Exception e) {
            return "attachment";
        }
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    /** 存储对象名：yyyy/MM/dd/uuid.扩展名（扩展名白名单化，避免路径穿越） */
    private String buildObjectName(String originalFilename) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String raw = originalFilename.substring(dot + 1);
                if (raw.matches("[A-Za-z0-9]{1,10}")) {
                    ext = "." + raw.toLowerCase();
                }
            }
        }
        return datePath + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
    }
}
