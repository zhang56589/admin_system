package com.example.adminsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.adminsystem.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
