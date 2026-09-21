package com.dataviz.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.file.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
