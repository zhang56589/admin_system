package com.example.adminsystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.adminsystem.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    SysUser login(String username,String password);

    boolean existUsername(String username);

    /** 修改用户角色 */
    boolean updateUserRole(Long id, Integer role);

    /** 启用/禁用用户 */
    boolean updateUserEnabled(Long id, Integer enabled);

    /** 重置用户密码（传入明文，内部BCrypt加密） */
    boolean resetUserPassword(Long id, String rawPassword);
}
