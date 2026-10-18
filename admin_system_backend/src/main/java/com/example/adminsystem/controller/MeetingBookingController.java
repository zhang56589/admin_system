package com.example.adminsystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adminsystem.annotation.RequireAdmin;
import com.example.adminsystem.dto.ApproveBookingDTO;
import com.example.adminsystem.dto.BookingDTO;
import com.example.adminsystem.dto.BookingQueryDTO;
import com.example.adminsystem.service.MeetingBookingService;
import com.example.adminsystem.util.Result;
import com.example.adminsystem.vo.BookingVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/booking")
public class MeetingBookingController {

    @Resource
    private MeetingBookingService meetingBookingService;

    /**
     * 员工提交预约
     */
    @PostMapping("/submit")
    public Result<?> submit(@Valid @RequestBody BookingDTO dto, HttpServletRequest request){
        Object userIdObj = request.getAttribute("userId");
        if(userIdObj == null){
            return Result.fail(401,"未登录，请携带token");
        }
        Long userId = Long.parseLong(userIdObj.toString());
        return meetingBookingService.submitBooking(userId, dto);
    }

    /**
     * 管理员：分页查询全部预约（支持会议室、状态、时间段筛选）
     */
    @RequireAdmin
    @GetMapping("/admin/page")
    public Result<Page<BookingVO>> adminPage(@RequestParam Integer pageNum,
                                             @RequestParam Integer pageSize,
                                             BookingQueryDTO dto) {
        return Result.success(meetingBookingService.adminPage(pageNum, pageSize, dto));
    }

    /**
     * 员工：查询自己的预约记录（支持会议室、状态、时间段筛选）
     */
    @GetMapping("/user/page")
    public Result<Page<BookingVO>> userPage(@RequestParam Integer pageNum,
                                            @RequestParam Integer pageSize,
                                            BookingQueryDTO dto,
                                            HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if(userIdObj == null){
            return Result.fail(401,"未登录，请携带token");
        }
        Long userId = Long.parseLong(userIdObj.toString());
        return Result.success(meetingBookingService.userPage(pageNum, pageSize, userId, dto));
    }

    /**
     * 管理员审批预约：通过/驳回
     */
    @RequireAdmin(message = "无权限，仅管理员可以执行审批操作")
    @PostMapping("/approve")
    public Result<?> approve(@Valid @RequestBody ApproveBookingDTO dto,
                             HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if(userIdObj == null){
            return Result.fail(401,"未登录，请携带token");
        }
        Long adminId = Long.parseLong(userIdObj.toString());
        return meetingBookingService.approve(dto, adminId);
    }

    /**
     * 员工取消预约，仅待审批可取消
     */
    @DeleteMapping("/cancel/{id}")
    public Result<?> cancel(@PathVariable Long id, HttpServletRequest request){
        Object userIdObj = request.getAttribute("userId");
        if(userIdObj == null){
            return Result.fail(401,"未登录");
        }
        Long userId = Long.parseLong(userIdObj.toString());
        return meetingBookingService.cancelBooking(id, userId);
    }

    /**
     * 管理员：多条件筛选分页查询预约
     */
    @RequireAdmin
    @GetMapping("/admin/query")
    public Result<Page<BookingVO>> adminQuery(@RequestParam Integer pageNum,
                                              @RequestParam Integer pageSize,
                                              BookingQueryDTO dto){
        return Result.success(meetingBookingService.queryPage(pageNum, pageSize, dto));
    }

    /**
     * 员工：本人预约带条件筛选
     */
    @GetMapping("/user/query")
    public Result<Page<BookingVO>> userQuery(@RequestParam Integer pageNum,
                                             @RequestParam Integer pageSize,
                                             BookingQueryDTO dto,
                                             HttpServletRequest request){
        Object userIdObj = request.getAttribute("userId");
        if(userIdObj == null){
            return Result.fail(401,"未登录，请携带token");
        }
        Long userId = Long.parseLong(userIdObj.toString());
        return Result.success(meetingBookingService.userQueryPage(pageNum, pageSize, dto, userId));
    }
}
