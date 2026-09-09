package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表访问。登录按用户名查一行；拦截器按 id 读 {@code token_version}。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
