package com.dataviz.file.service;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.file.dto.FileQueryDTO;
import com.dataviz.file.dto.FileUploadDTO;
import com.dataviz.file.vo.FileInfoVO;
import com.dataviz.file.vo.FileUploadResultVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

public interface FileService {

    FileUploadResultVO upload(MultipartFile file, FileUploadDTO uploadDTO);

    List<FileUploadResultVO> uploadBatch(List<MultipartFile> files, FileUploadDTO uploadDTO);

    void download(Long id, HttpServletResponse response);

    /** 图片直读（免登，仅 image/*，供 <img> 与 CSS 背景使用） */
    void view(Long id, HttpServletResponse response);

    FileInfoVO getFileInfo(Long id);

    void deleteFile(Long id);

    PageResult<FileInfoVO> listFiles(FileQueryDTO queryDTO);
}
