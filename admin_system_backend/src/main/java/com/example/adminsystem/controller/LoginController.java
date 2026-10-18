package com.example.adminsystem.controller;

import com.example.adminsystem.dto.RegisterDTO;
import com.example.adminsystem.entity.SysUser;
import com.example.adminsystem.service.SysUserService;
import com.example.adminsystem.util.JwtUtil;
import com.example.adminsystem.util.PasswordUtil;
import com.example.adminsystem.util.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginController {

    @Resource
    private SysUserService sysUserService;

    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String,Object>> login(@RequestBody Map<String,String> body){







        String username = body.get("username");
        String password = body.get("password");
        SysUser user = sysUserService.login(username,password);
        if(user == null){
            return Result.fail(400,"用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        Map<String,Object> data = new HashMap<>();
        data.put("role", user.getRole());
        data.put("token",token);
        data.put("userId",user.getId());
        data.put("username",user.getUsername());
        return Result.success(data);
    }

    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody RegisterDTO dto){
        // 判断用户名是否重复
        boolean exist = sysUserService.existUsername(dto.getUsername());
        if(exist){
            return Result.fail(400,"用户名已存在");
        }
        SysUser sysUser = new SysUser();
        sysUser.setUsername(dto.getUsername());
        sysUser.setNickname(dto.getNickname());
        sysUser.setPassword(PasswordUtil.encrypt(dto.getPassword()));
        // 默认注册普通员工，禁止前端传入role
        sysUser.setRole(0);
        sysUserService.save(sysUser);
        return Result.success("注册成功");
    }
}
