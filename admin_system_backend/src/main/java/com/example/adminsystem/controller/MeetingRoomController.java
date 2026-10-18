package com.example.adminsystem.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adminsystem.annotation.RequireAdmin;
import com.example.adminsystem.entity.MeetingBooking;
import com.example.adminsystem.entity.MeetingRoom;
import com.example.adminsystem.enums.BookingStatusEnum;
import com.example.adminsystem.enums.RoomStatusEnum;
import com.example.adminsystem.mapper.MeetingBookingMapper;
import com.example.adminsystem.service.MeetingRoomService;
import com.example.adminsystem.util.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room")
public class MeetingRoomController {

    @Resource
    private MeetingRoomService meetingRoomService;

    @Resource
    private MeetingBookingMapper meetingBookingMapper;

    /**
     * 管理员分页全部会议室
     */
    @RequireAdmin
    @GetMapping("/page")
    public Result<Page<MeetingRoom>> page(@RequestParam Integer pageNum,
                                          @RequestParam Integer pageSize){
        Page<MeetingRoom> page = new Page<>(pageNum,pageSize);
        Page<MeetingRoom> pageData = meetingRoomService.page(page, new LambdaQueryWrapper<>());
        return Result.success(pageData);
    }

    /**
     * 管理员新增会议室
     */
    @RequireAdmin
    @PostMapping("/add")
    public Result<String> add(@RequestBody MeetingRoom room){
        room.setId(null);
        boolean ok = meetingRoomService.save(room);
        if(ok){
            return Result.success("新增成功");
        }else{
            return Result.fail(400,"新增失败，请检查参数");
        }
    }

    /**
     * 管理员编辑会议室
     */
    @RequireAdmin
    @PutMapping("/update")
    public Result<String> update(@RequestBody MeetingRoom room){
        //只更新不为null的字段，避免null覆盖数据库
        LambdaUpdateWrapper<MeetingRoom> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MeetingRoom::getId,room.getId())
                .set(room.getRoomName()!=null,MeetingRoom::getRoomName,room.getRoomName())
                .set(room.getCapacity()!=null,MeetingRoom::getCapacity,room.getCapacity())
                .set(room.getLocation()!=null,MeetingRoom::getLocation,room.getLocation())
                .set(room.getDescription()!=null,MeetingRoom::getDescription,room.getDescription())
                .set(room.getStatus()!=null,MeetingRoom::getStatus,room.getStatus());
        boolean ok = meetingRoomService.update(wrapper);
        if(ok){
            return Result.success("修改成功");
        }else{
            return Result.fail(400,"修改失败，请检查参数");
        }
    }

    /**
     * 管理员删除会议室
     * 前置校验：存在待审批/已通过的未完结预约时禁止删除
     */
    @RequireAdmin
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id){
        if(id == null){
            return Result.fail(400,"会议室id不能为空");
        }
        // 校验是否存在未完结（待审批/已通过）预约
        LambdaQueryWrapper<MeetingBooking> bookingWrapper = new LambdaQueryWrapper<>();
        bookingWrapper.eq(MeetingBooking::getRoomId, id)
                .in(MeetingBooking::getResStatus,
                        BookingStatusEnum.PENDING.getCode(),
                        BookingStatusEnum.PASS.getCode());
        Long count = meetingBookingMapper.selectCount(bookingWrapper);
        if(count != null && count > 0){
            return Result.fail(400,"该会议室存在未完结预约，无法删除");
        }
        boolean ok = meetingRoomService.removeById(id);
        if(ok){
            return Result.success("删除成功");
        }else{
            return Result.fail(400,"删除失败，请检查会议室是否存在");
        }
    }

    /**
     * 员工查询可用会议室，无需管理员
     */
    @GetMapping("/list")
    public Result<?> list(){
        LambdaQueryWrapper<MeetingRoom> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MeetingRoom::getStatus, RoomStatusEnum.ENABLE.getCode());
        return Result.success(meetingRoomService.list(wrapper));
    }

    /**
     * 管理员启用会议室
     */
    @RequireAdmin
    @PutMapping("/enable/{id}")
    public Result<String> enable(@PathVariable Long id){
        LambdaUpdateWrapper<MeetingRoom> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MeetingRoom::getId,id).set(MeetingRoom::getStatus,RoomStatusEnum.ENABLE.getCode());
        boolean ok = meetingRoomService.update(wrapper);
        if(ok){
            return Result.success("启用成功");
        }else{
            return Result.fail(400,"启用失败，请检查会议室是否存在");
        }
    }

    /**
     * 管理员禁用会议室
     */
    @RequireAdmin
    @PutMapping("/disable/{id}")
    public Result<String> disable(@PathVariable Long id){
        LambdaUpdateWrapper<MeetingRoom> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MeetingRoom::getId,id).set(MeetingRoom::getStatus,RoomStatusEnum.DISABLE.getCode());
        boolean ok = meetingRoomService.update(wrapper);
        if(ok){
            return Result.success("禁用成功");
        }else{
            return Result.fail(400,"禁用失败，请检查会议室是否存在");
        }
    }
}
