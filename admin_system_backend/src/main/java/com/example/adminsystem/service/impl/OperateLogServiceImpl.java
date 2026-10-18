package com.example.adminsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.adminsystem.entity.OperationLog;
import com.example.adminsystem.mapper.OperationLogMapper;
import com.example.adminsystem.service.OperateLogService;
import org.springframework.stereotype.Service;

@Service
public class OperateLogServiceImpl
        extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperateLogService {
}
