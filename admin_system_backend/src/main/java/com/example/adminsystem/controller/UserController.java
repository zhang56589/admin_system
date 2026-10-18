package com.example.adminsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adminsystem.annotation.RequireAdmin;
import com.example.adminsystem.dto.UserPasswordDTO;
import com.example.adminsystem.dto.UserRoleDTO;
import com.example.adminsystem.dto.UserStatusDTO;
import com.example.adminsystem.entity.SysUser;
import com.example.adminsystem.service.SysUserService;
import com.example.adminsystem.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private SysUserService sysUserService;

    /**
     * 管理员分页查询用户列表
     */
    @RequireAdmin
    @GetMapping("/page")
    public Result<Page<SysUser>> page(@RequestParam Integer pageNum,
                                      @RequestParam Integer pageSize){
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> pageData = sysUserService.page(page, wrapper);
        return Result.success(pageData);
    }

    /**
     * 修改用户角色
     */
    @RequireAdmin
    @PutMapping("/role")
    public Result<Void> updateRole(@RequestBody UserRoleDTO dto, HttpServletRequest request) {
        log.info("=== 调试 updateRole === Content-Type={}, body={}", request.getContentType(), dto);
        if (dto == null || dto.getId() == null) {
            return Result.fail(400, "用户id不能为空");
        }
        if (dto.getRole() == null) {
            return Result.fail(400, "角色参数不能为空");
        }
        boolean ok = sysUserService.updateUserRole(dto.getId(), dto.getRole());
        if (!ok) {
            return Result.fail(404, "用户不存在或更新失败");
        }
        return Result.success();
    }

    /**
     * 启用/禁用用户
     */
    @RequireAdmin
    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestBody UserStatusDTO dto) {
        if (dto == null || dto.getId() == null) {
            return Result.fail(400, "用户id不能为空");
        }
        if (dto.getEnabled() == null) {
            return Result.fail(400, "启用状态参数不能为空");
        }
        boolean ok = sysUserService.updateUserEnabled(dto.getId(), dto.getEnabled());
        if (!ok) {
            return Result.fail(404, "用户不存在或更新失败");
        }
        return Result.success();
    }

    /**
     * 重置用户密码
     */
    @RequireAdmin
    @PutMapping("/resetPassword")
    public Result<Void> resetPassword(@RequestBody UserPasswordDTO dto) {
        if (dto == null || dto.getId() == null) {
            return Result.fail(400, "用户id不能为空");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            return Result.fail(400, "新密码不能为空");
        }
        boolean ok = sysUserService.resetUserPassword(dto.getId(), dto.getPassword());
        if (!ok) {
            return Result.fail(404, "用户不存在或重置失败");
        }
        return Result.success();
    }
}
