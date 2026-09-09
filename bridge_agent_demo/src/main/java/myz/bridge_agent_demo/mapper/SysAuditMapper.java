package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.SysAudit;
import org.apache.ibatis.annotations.Mapper;

/** 权限审计表。只追加、按时间倒序查。 */
@Mapper
public interface SysAuditMapper extends BaseMapper<SysAudit> {
}
