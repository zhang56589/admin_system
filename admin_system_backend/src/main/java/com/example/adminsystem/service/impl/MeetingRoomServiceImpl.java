package com.example.adminsystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.adminsystem.entity.MeetingRoom;
import com.example.adminsystem.mapper.MeetingRoomMapper;
import com.example.adminsystem.service.MeetingRoomService;
import org.springframework.stereotype.Service;

@Service
public class MeetingRoomServiceImpl extends ServiceImpl<MeetingRoomMapper, MeetingRoom> implements MeetingRoomService {
}
