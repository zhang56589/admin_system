package com.example.adminsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adminsystem.annotation.RequireAdmin;
import com.example.adminsystem.entity.OperationLog;
import com.example.adminsystem.service.OperateLogService;
import com.example.adminsystem.util.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/log")
public class LogController {

    @Resource
    private OperateLogService operateLogService;

    /**
     * 管理员分页查询操作日志
     */
    @RequireAdmin
    @GetMapping("/page")
    public Result<Page<OperationLog>> page(@RequestParam Integer pageNum,
                                           @RequestParam Integer pageSize){
        Page<OperationLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(OperationLog::getCreateTime);
        Page<OperationLog> pageData = operateLogService.page(page, wrapper);
        return Result.success(pageData);
    }
}
