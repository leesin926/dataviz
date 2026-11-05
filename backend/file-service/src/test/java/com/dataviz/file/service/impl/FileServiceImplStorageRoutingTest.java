package com.dataviz.file.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.file.dto.FileQueryDTO;
import com.dataviz.file.dto.FileUploadDTO;
import com.dataviz.file.entity.FileInfo;
import com.dataviz.file.mapper.FileMapper;
import com.dataviz.file.service.strategy.StorageStrategy;
import com.dataviz.file.service.strategy.StorageStrategyRouter;
import com.dataviz.file.vo.FileInfoVO;
import com.dataviz.file.vo.FileUploadResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存储策略"可选"落到上传/读取/删除链路上的判据（BE3）。
 * <p>这里用真实的 {@link StorageStrategyRouter}（配置驱动）+ mock 的两个策略 bean，
 * 守的是四件事：配置选谁就真用谁、谁处理的就落谁的 code、缺 minio 配置时<b>不落库</b>直接拒、
 * 读/删按<b>行自己记录的</b>策略走而不是按当前配置走。MinIO 没起也能跑——策略是 mock。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplStorageRoutingTest {

    private static final String SECRET = "s3cr3t-DO-NOT-LEAK";
    private static final String BUCKET = "dataviz";

    @Mock
    private FileMapper fileMapper;

    private StorageStrategy local;
    private StorageStrategy minio;

    @BeforeEach
    void setUp() {
        local = mock(StorageStrategy.class);
        minio = mock(StorageStrategy.class);
    }

    private FileServiceImpl serviceWith(String type, String endpoint, String bucket, String accessKey, String secretKey) {
        Map<String, StorageStrategy> strategies = new LinkedHashMap<>();
        strategies.put("localStorageStrategy", local);
        strategies.put("minioStorageStrategy", minio);
        StorageStrategyRouter router = new StorageStrategyRouter(strategies, type, endpoint, bucket, accessKey, secretKey);
        FileServiceImpl service = new FileServiceImpl(fileMapper, router);
        ReflectionTestUtils.setField(service, "bucket", BUCKET); // 复刻 @Value("${minio.bucket}")
        return service;
    }

    private FileServiceImpl localService() {
        return serviceWith("local", "", "", "", "");
    }

    private FileServiceImpl minioService() {
        return serviceWith("minio", "http://minio.internal:9000", BUCKET, "minioadmin", SECRET);
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "chart.png", "image/png", "bytes".getBytes(StandardCharsets.UTF_8));
    }

    /** 模拟 MyBatis-Plus 自增主键回填，让 fileUrl/VO.id 有真实值可断言 */
    private void givenInsertAssignsId(long id) {
        when(fileMapper.insert(any(FileInfo.class))).thenAnswer(inv -> {
            inv.getArgument(0, FileInfo.class).setId(id);
            return 1;
        });
    }

    // ---------- 写侧：配置选谁 + 落谁的 code ----------

    @Test
    @DisplayName("配置 local - 上传走本地策略，落库与返回都记 local")
    void should_useLocalStrategyAndRecordLocal_when_configSelectsLocal() {
        FileServiceImpl service = localService();
        givenInsertAssignsId(101L);

        FileUploadResultVO vo = service.upload(pngFile(), new FileUploadDTO());

        verify(local).upload(any(MockMultipartFile.class), eq(BUCKET), any(String.class));
        verify(minio, never()).upload(any(), any(), any());

        ArgumentCaptor<FileInfo> row = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileMapper).insert(row.capture());
        assertThat(row.getValue().getStorageType()).isEqualTo("local");
        assertThat(row.getValue().getId()).isEqualTo(101L);

        // 断言真实返回字段，不是非空
        assertThat(vo.getId()).isEqualTo(101L);
        assertThat(vo.getStorageType()).isEqualTo("local");
        assertThat(vo.getOriginalName()).isEqualTo("chart.png");
        assertThat(vo.getContentType()).isEqualTo("image/png");
        assertThat(vo.getFileUrl()).isEqualTo("/api/file/view/101"); // image/* 走免登 view
        assertThat(vo.getFileSize()).isEqualTo(5L); // "bytes".length
    }

    @Test
    @DisplayName("配置 minio 且必填项齐全 - 上传走 minio 策略，落库与返回都记 minio")
    void should_useMinioStrategyAndRecordMinio_when_configSelectsMinioWithCompleteConfig() {
        FileServiceImpl service = minioService();
        givenInsertAssignsId(202L);

        FileUploadResultVO vo = service.upload(pngFile(), new FileUploadDTO());

        verify(minio).upload(any(MockMultipartFile.class), eq(BUCKET), any(String.class));
        verify(local, never()).upload(any(), any(), any());

        ArgumentCaptor<FileInfo> row = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileMapper).insert(row.capture());
        assertThat(row.getValue().getStorageType()).isEqualTo("minio");
        assertThat(row.getValue().getFilePath()).isEqualTo(vo.getStoredName());

        assertThat(vo.getStorageType()).isEqualTo("minio");
        assertThat(vo.getId()).isEqualTo(202L);
        assertThat(vo.getFileUrl()).isEqualTo("/api/file/view/202");
    }

    @Test
    @DisplayName("上传落库前先按配置校验：bucket 缺省时仍取注入的 minio.bucket")
    void should_useInjectedBucket_when_dtoHasNoBucket() {
        FileServiceImpl service = minioService();
        givenInsertAssignsId(1L);
        FileUploadDTO dto = new FileUploadDTO();
        dto.setBucketName("reports");

        service.upload(pngFile(), dto);

        verify(minio).upload(any(), eq("reports"), any(String.class));
        ArgumentCaptor<FileInfo> row = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileMapper).insert(row.capture());
        assertThat(row.getValue().getBucket()).isEqualTo("reports");
    }

    // ---------- 配置缺失：拒绝且什么都不落库 ----------

    @Test
    @DisplayName("配置 minio 但缺 access-key - 上传即拒，不写任何存储也不落库")
    void should_refuseAndPersistNothing_when_minioSelectedButAccessKeyMissing() {
        FileServiceImpl service = serviceWith("minio", "http://minio.internal:9000", BUCKET, "", SECRET);

        assertThatThrownBy(() -> service.upload(pngFile(), new FileUploadDTO()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.access-key")
                .hasMessageNotContaining(SECRET);

        verify(fileMapper, never()).insert(any());
        verify(minio, never()).upload(any(), any(), any());
        verify(local, never()).upload(any(), any(), any());
    }

    // ---------- 读/删：按行记录的策略路由 ----------

    @Test
    @DisplayName("行记 local（当前配置已切 minio）- 仍按行路由到 local 读，不被当前配置误导")
    void should_routeReadByRowType_when_configSwitchedButRowSaysLocal() throws Exception {
        FileServiceImpl service = minioService(); // 当前配置=minio，但行是历史 local
        FileInfo row = fileInfo(5L, "local", BUCKET, "2026-09-21/a.png", "image/png");
        when(fileMapper.selectById(5L)).thenReturn(row);
        lenient().when(local.download(BUCKET, "2026-09-21/a.png"))
                .thenReturn(new ByteArrayInputStream("PNGDATA".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.download(5L, response);

        verify(local).download(BUCKET, "2026-09-21/a.png");
        verify(minio, never()).download(any(), any());
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo("PNGDATA");
        assertThat(response.getContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("行记 minio - 删除走 minio 策略删对象，再删库行")
    void should_deleteViaRowStrategy_when_rowSaysMinio() {
        FileServiceImpl service = localService(); // 当前配置=local，但行是 minio
        String objectName = "2026/10/01/x.mp4";
        FileInfo row = fileInfo(7L, "minio", "media", objectName, "video/mp4");
        when(fileMapper.selectById(7L)).thenReturn(row);

        service.deleteFile(7L);

        verify(minio).delete("media", "2026/10/01/x.mp4");
        verify(local, never()).delete(any(), any());
        verify(fileMapper).deleteById(7L);
    }

    @Test
    @DisplayName("行 storage_type 为空/不可路由 - 删除明确拒绝，且不删库行（宁可留可见的不一致）")
    void should_refuseDeleteAndKeepRow_when_rowStorageTypeNotRoutable() {
        FileServiceImpl service = localService();
        FileInfo row = fileInfo(9L, null, BUCKET, "2026-09-21/orphan.png", "image/png");
        when(fileMapper.selectById(9L)).thenReturn(row);

        assertThatThrownBy(() -> service.deleteFile(9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("storage_type");

        verify(fileMapper, never()).deleteById(anyLong());
        verify(local, never()).delete(any(), any());
        verify(minio, never()).delete(any(), any());
    }

    // ---------- 既有批量/列表链路保持可用 ----------

    @Test
    @DisplayName("批量上传 - 每个文件各落一行、各记同一策略，返回条数与顺序一致")
    void should_uploadBatchAndRecordEachRow_when_configLocal() {
        FileServiceImpl service = localService();
        givenInsertAssignsId(11L);
        MockMultipartFile f1 = new MockMultipartFile("files", "a.png", "image/png", "x".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("files", "b.pdf", "application/pdf", "yy".getBytes());

        List<FileUploadResultVO> results = service.uploadBatch(Arrays.asList(f1, f2), new FileUploadDTO());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(FileUploadResultVO::getOriginalName).containsExactly("a.png", "b.pdf");
        assertThat(results).extracting(FileUploadResultVO::getStorageType).containsOnly("local");
        // image → view；pdf → download（既有 URL 语义未回退）
        assertThat(results.get(0).getFileUrl()).startsWith("/api/file/view/");
        assertThat(results.get(1).getFileUrl()).startsWith("/api/file/download/");
        verify(local, org.mockito.Mockito.times(2)).upload(any(), eq(BUCKET), any(String.class));
    }

    @Test
    @DisplayName("列表查询 - storage_type 随 VO 透出，供前端定位文件真实所在")
    void should_carryStorageTypeIntoVo_when_listFiles() {
        FileServiceImpl service = minioService();
        FileInfo row = fileInfo(3L, "minio", BUCKET, "2026-10-02/c.png", "image/png");
        Page<FileInfo> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(row));
        page.setTotal(1L);
        when(fileMapper.selectPage(any(Page.class), any())).thenReturn(page);
        FileQueryDTO query = new FileQueryDTO();

        PageResult<FileInfoVO> result = service.listFiles(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        FileInfoVO vo = result.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(3L);
        assertThat(vo.getStorageType()).isEqualTo("minio");
        assertThat(vo.getBucket()).isEqualTo(BUCKET);
    }

    @Test
    @DisplayName("取单个文件信息 - 落库的 storage_type 原样返回")
    void should_exposeRecordedStorageType_when_getFileInfo() {
        FileServiceImpl service = localService();
        FileInfo row = fileInfo(4L, "local", BUCKET, "2026-09-21/d.png", "image/png");
        row.setFileName("d.png");
        row.setFileSize(3L);
        row.setCreateTime(LocalDateTime.now());
        when(fileMapper.selectById(4L)).thenReturn(row);

        FileInfoVO vo = service.getFileInfo(4L);

        assertThat(vo.getId()).isEqualTo(4L);
        assertThat(vo.getFileName()).isEqualTo("d.png");
        assertThat(vo.getStorageType()).isEqualTo("local");
    }

    private static FileInfo fileInfo(Long id, String storageType, String bucket, String path, String type) {
        return FileInfo.builder()
                .id(id)
                .fileName(path.substring(path.lastIndexOf('/') + 1))
                .filePath(path)
                .fileSize(5L)
                .fileType(type)
                .bucket(bucket)
                .storageType(storageType)
                .createTime(LocalDateTime.now())
                .build();
    }
}
