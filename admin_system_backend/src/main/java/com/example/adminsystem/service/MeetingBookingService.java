package com.example.adminsystem.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.adminsystem.dto.ApproveBookingDTO;
import com.example.adminsystem.dto.BookingDTO;
import com.example.adminsystem.dto.BookingQueryDTO;
import com.example.adminsystem.entity.MeetingBooking;
import com.example.adminsystem.util.Result;
import com.example.adminsystem.vo.BookingVO;

public interface MeetingBookingService extends IService<MeetingBooking> {

    Result<?> submitBooking(Long userId, BookingDTO dto);

    Result<?> approve(ApproveBookingDTO dto, Long adminId);

    /**管理员：查询全部预约分页 返回VO*/
    Page<BookingVO> adminPage(Integer pageNum, Integer pageSize, BookingQueryDTO dto);

    /**员工：查询本人预约分页 返回VO*/
    Page<BookingVO> userPage(Integer pageNum, Integer pageSize, Long userId, BookingQueryDTO dto);

    /**员工取消预约*/
    Result<?> cancelBooking(Long bookingId, Long userId);

    /**管理员多条件筛选分页 返回VO*/
    Page<BookingVO> queryPage(Integer pageNum, Integer pageSize, BookingQueryDTO dto);

    /**员工本人预约带条件筛选 返回VO*/
    Page<BookingVO> userQueryPage(Integer pageNum, Integer pageSize, BookingQueryDTO dto, Long userId);
}
