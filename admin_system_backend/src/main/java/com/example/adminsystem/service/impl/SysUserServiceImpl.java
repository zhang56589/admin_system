package com.example.adminsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.adminsystem.entity.SysUser;
import com.example.adminsystem.mapper.SysUserMapper;
import com.example.adminsystem.service.SysUserService;
import com.example.adminsystem.util.PasswordUtil;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    @Override
    public boolean existUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername,username);
        return this.exists(wrapper);
    }

    @Override
    public SysUser login(String username, String password) {
        LambdaQueryWrapper<SysUser> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername,username);
        SysUser user = getOne(wrapper,false);
        //用户名不存在
        if(user == null){
            return null;
        }
        // 账户已禁用，禁止登录
        if(user.getEnabled() != null && user.getEnabled() != 1){
            return null;
        }
        //BCrypt校验密码，明文password对比数据库密文user.getPassword()
        boolean ok = PasswordUtil.matches(password, user.getPassword());
        if(ok){
            return user;
        }
        return null;
    }

    @Override
    public boolean updateUserRole(Long id, Integer role) {
        SysUser user = getById(id);
        if (user == null) {
            return false;
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setRole(role);
        return updateById(update);
    }

    @Override
    public boolean updateUserEnabled(Long id, Integer enabled) {
        SysUser user = getById(id);
        if (user == null) {
            return false;
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setEnabled(enabled);
        return updateById(update);
    }

    @Override
    public boolean resetUserPassword(Long id, String rawPassword) {
        SysUser user = getById(id);
        if (user == null) {
            return false;
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(PasswordUtil.encrypt(rawPassword));
        return updateById(update);
    }
}
