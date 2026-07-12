package com.wushi.module.spider.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wushi.module.spider.audit.entity.DataSyncAuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataSyncAuditLogMapper extends BaseMapper<DataSyncAuditLogEntity> {
}
