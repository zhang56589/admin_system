package com.example.adminsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.adminsystem.dto.ApproveBookingDTO;
import com.example.adminsystem.dto.BookingDTO;
import com.example.adminsystem.dto.BookingQueryDTO;
import com.example.adminsystem.entity.MeetingBooking;
import com.example.adminsystem.entity.MeetingRoom;
import com.example.adminsystem.entity.OperationLog;
import com.example.adminsystem.entity.SysUser;
import com.example.adminsystem.enums.BookingStatusEnum;
import com.example.adminsystem.enums.RoomStatusEnum;
import com.example.adminsystem.mapper.MeetingBookingMapper;
import com.example.adminsystem.mapper.MeetingRoomMapper;
import com.example.adminsystem.mapper.OperationLogMapper;
import com.example.adminsystem.mapper.SysUserMapper;
import com.example.adminsystem.service.BookingNotificationProducer;
import com.example.adminsystem.service.MeetingBookingService;
import com.example.adminsystem.util.Result;
import com.example.adminsystem.vo.BookingVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MeetingBookingServiceImpl
        extends ServiceImpl<MeetingBookingMapper, MeetingBooking>
        implements MeetingBookingService {

    /**
     * 分布式锁 key 前缀：按会议室粒度串行化同房间的所有预约操作。
     * 同一会议室 ID 对应的锁 Key：booking:room:{roomId}
     * 为何锁会议室而非「会议室 + 时间段」？
     *   两个不同时间段（如 10:00-12:00 与 11:00-13:00）的锁 key 不同，
     *   但实际时间重叠会在锁内 hasTimeConflict 校验时被 DB 层拦截。
     *   锁会议室粒度更简单、覆盖更全，不会漏判。
     */
    private static final String LOCK_KEY_PREFIX = "booking:room:";

    /** 抢锁最长等待时间：5 秒；超过则放弃返回友好提示 */
    private static final long LOCK_WAIT_SECONDS = 5L;

    /** leaseTime=-1 由 Redisson watch dog 自动续期，每 10s 续到 30s，防止业务执行超时提前释放 */
    private static final long LOCK_LEASE_TIME = -1L;

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private MeetingRoomMapper meetingRoomMapper;

    /** Redisson 分布式锁客户端，由 redisson-spring-boot-starter 自动装配 */
    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SysUserMapper sysUserMapper;

    /**
     * 预约通知异步生产者 —— 可选：仅当 rocketmq.name-server 配置时 Bean 存在，
     * 未配置时此字段为 null，跳过发送，不阻塞主流程。
     */
    @Autowired(required = false)
    private BookingNotificationProducer bookingNotificationProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> submitBooking(Long userId, BookingDTO dto) {
        if(dto.getRoomId() == null || dto.getStartTime() == null || dto.getEndTime() == null){
            return Result.fail(400,"预约参数不能为空");
        }
        if(dto.getTitle() == null || dto.getTitle().trim().isEmpty()){
            return Result.fail(400,"会议主题不能为空");
        }

        LocalDateTime startTime = dto.getStartTime();
        LocalDateTime endTime = dto.getEndTime();
        Long roomId = dto.getRoomId();

        if(startTime.isAfter(endTime)){
            return Result.fail(400,"开始时间不能晚于结束时间");
        }
        if(startTime.isBefore(LocalDateTime.now())){
            return Result.fail(400,"不能预约过去时间");
        }

        // ===== 分布式锁：应用层第一道防线，防止多实例并发同一会议室 =====
        String lockKey = LOCK_KEY_PREFIX + roomId;
        RLock rLock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = rLock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                log.info("预约提交抢锁失败, lockKey={}, userId={}", lockKey, userId);
                return Result.fail(400, "该会议室正在被他人预约，请稍后重试");
            }

            MeetingRoom room = meetingRoomMapper.selectByIdForUpdate(roomId);
            if(room == null){
                return Result.fail(400,"会议室不存在");
            }
            if(RoomStatusEnum.DISABLE.getCode().equals(room.getStatus())){
                return Result.fail(400,"该会议室已禁用，无法预约");
            }

            // ===== 锁内 DB 层时间冲突校验：并发请求串行化后逐个落库，已占用时间段被拦截 =====
            if(hasTimeConflict(roomId, startTime, endTime, null)){
                return Result.fail(400,"该会议室此时间段已被预约，时间冲突");
            }

            MeetingBooking booking = new MeetingBooking();
            booking.setRoomId(roomId);
            booking.setUserId(userId);
            booking.setStartTime(startTime);
            booking.setEndTime(endTime);
            booking.setTitle(dto.getTitle());
            booking.setReason(dto.getReason());
            booking.setResStatus(BookingStatusEnum.PENDING.getCode());
            save(booking);

            saveOperateLog(userId,"预约会议室",
                    "提交预约，roomId:"+roomId+"，时间段："+startTime+" ~ "+endTime+"，主题："+dto.getTitle());

            // ===== 异步通知（可选：未配置 MQ 时 Producer 为 null，跳过发送） =====
            if (bookingNotificationProducer != null) {
                try {
                    ServletRequestAttributes attributes =
                            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    String username = attributes != null
                            && attributes.getRequest().getAttribute("username") != null
                            ? attributes.getRequest().getAttribute("username").toString() : "";
                    bookingNotificationProducer.sendSubmitNotify(
                            booking.getId(), username, room.getRoomName(),
                            startTime.toString(), endTime.toString(), userId);
                } catch (Exception ex) {
                    log.warn("MQ预约提交通知发送跳过: {}", ex.getMessage());
                }
            }

            return Result.success("预约提交成功，等待管理员审批");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("预约提交线程被中断, lockKey={}", lockKey, e);
            return Result.fail(400, "操作被中断，请稍后重试");
        } finally {
            // 防误解锁：只释放当前线程持有的锁
            if (locked && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> approve(ApproveBookingDTO dto, Long adminId) {
        MeetingBooking current = this.getById(dto.getBookingId());
        if(current == null){
            return Result.fail(400,"预约记录不存在");
        }

        // 审批(PASS)同样影响会议室时间片，用分布式锁串行化
        String lockKey = LOCK_KEY_PREFIX + current.getRoomId();
        RLock rLock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = rLock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                log.info("预约审批抢锁失败, lockKey={}, adminId={}", lockKey, adminId);
                return Result.fail(400, "该会议室正在处理其他预约，请稍后重试");
            }

            meetingRoomMapper.selectByIdForUpdate(current.getRoomId());
            MeetingBooking booking = baseMapper.selectByIdForUpdate(dto.getBookingId());
            if(booking == null){
                return Result.fail(400,"预约记录不存在");
            }
            if(!booking.getResStatus().equals(BookingStatusEnum.PENDING.getCode())){
                return Result.fail(400,"该预约已审批，不可重复操作");
            }

            Integer status = dto.getStatus();
            if(!BookingStatusEnum.PASS.getCode().equals(status) && !BookingStatusEnum.REJECT.getCode().equals(status)){
                return Result.fail(400,"非法审批状态");
            }

            if(BookingStatusEnum.PASS.getCode().equals(status)
                    && hasTimeConflict(booking.getRoomId(), booking.getStartTime(), booking.getEndTime(), booking.getId())){
                return Result.fail(400,"该会议室此时间段已被预约，时间冲突");
            }

            if(BookingStatusEnum.REJECT.getCode().equals(dto.getStatus())){
                if(dto.getRejectReason() == null || dto.getRejectReason().trim().isEmpty()){
                    return Result.fail(400,"驳回必须填写驳回原因");
                }
                booking.setRejectReason(dto.getRejectReason());
            }else{
                booking.setRejectReason(null);
            }
            booking.setResStatus(dto.getStatus());
            this.updateById(booking);

            String resultText = BookingStatusEnum.PASS.getCode().equals(dto.getStatus()) ? "通过":"驳回";
            saveOperateLog(adminId,"预约审批","审批预约id="+dto.getBookingId()+"，审批结果："+resultText);

            // ===== 异步通知申请人（可选：未配置 MQ 时跳过） =====
            if (bookingNotificationProducer != null) {
                try {
                    SysUser applicant = sysUserMapper.selectById(booking.getUserId());
                    String resultCode = BookingStatusEnum.PASS.getCode().equals(dto.getStatus()) ? "PASS" : "REJECT";
                    bookingNotificationProducer.sendApproveNotify(
                            booking.getId(),
                            applicant != null ? applicant.getUsername() : "",
                            resultCode,
                            booking.getRejectReason(),
                            adminId);
                } catch (Exception ex) {
                    log.warn("MQ审批通知发送跳过: {}", ex.getMessage());
                }
            }

            return Result.success("审批完成");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("预约审批线程被中断, lockKey={}", lockKey, e);
            return Result.fail(400, "操作被中断，请稍后重试");
        } finally {
            if (locked && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }

    @Override
    public Page<BookingVO> adminPage(Integer pageNum, Integer pageSize, BookingQueryDTO dto) {
        return queryPage(pageNum, pageSize, dto);
    }

    @Override
    public Page<BookingVO> userPage(Integer pageNum, Integer pageSize, Long userId, BookingQueryDTO dto) {
        return userQueryPage(pageNum, pageSize, dto, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> cancelBooking(Long bookingId, Long userId) {
        MeetingBooking current = getById(bookingId);
        if(current == null){
            return Result.fail(400,"预约不存在");
        }

        meetingRoomMapper.selectByIdForUpdate(current.getRoomId());
        MeetingBooking booking = baseMapper.selectByIdForUpdate(bookingId);
        if(booking == null){
            return Result.fail(400,"预约不存在");
        }
        if(!booking.getUserId().equals(userId)){
            return Result.fail(400,"只能取消本人的预约");
        }
        if(!(BookingStatusEnum.PENDING.getCode().equals(booking.getResStatus())
                || BookingStatusEnum.PASS.getCode().equals(booking.getResStatus()))){
            return Result.fail(400,"仅待审批、已通过的预约可以取消");
        }
        if(booking.getStartTime().isBefore(LocalDateTime.now())){
            return Result.fail(400,"会议已开始，无法取消");
        }

        booking.setResStatus(BookingStatusEnum.CANCEL.getCode());
        updateById(booking);
        saveOperateLog(userId,"取消预约","取消预约id:" + bookingId);
        return Result.success("取消预约成功");
    }

    @Override
    public Page<BookingVO> queryPage(Integer pageNum, Integer pageSize, BookingQueryDTO dto) {
        Page<BookingVO> page = buildPage(pageNum, pageSize);
        BookingQueryDTO query = dto == null ? new BookingQueryDTO() : dto;
        return baseMapper.selectBookingVOPage(page, query);
    }

    @Override
    public Page<BookingVO> userQueryPage(Integer pageNum, Integer pageSize, BookingQueryDTO dto, Long userId) {
        Page<BookingVO> page = buildPage(pageNum, pageSize);
        BookingQueryDTO query = dto == null ? new BookingQueryDTO() : dto;
        return baseMapper.selectUserBookingVOPage(page, userId, query);
    }

    /**
     * 同一会议室、待审批或已通过的时间段是否重叠。
     * 调用前需已对 meeting_room 行加 FOR UPDATE，避免并发下先查后写。
     * 在分布式锁内调用时，并发请求已被 Redisson 锁串行化，此处为 DB 层最终兜底。
     */
    private boolean hasTimeConflict(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long excludeBookingId) {
        LambdaQueryWrapper<MeetingBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MeetingBooking::getRoomId, roomId)
                .in(MeetingBooking::getResStatus, BookingStatusEnum.PENDING.getCode(), BookingStatusEnum.PASS.getCode())
                .lt(MeetingBooking::getStartTime, endTime)
                .gt(MeetingBooking::getEndTime, startTime);
        if (excludeBookingId != null) {
            wrapper.ne(MeetingBooking::getId, excludeBookingId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }

    private Page<BookingVO> buildPage(Integer pageNum, Integer pageSize){
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        return new Page<>(num, size);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveOperateLog(Long userId, String operateType, String content){
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setOperateType(operateType);
        log.setContent(content);

        // 从当前请求上下文自动补齐 username / method / ip
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if(attributes != null){
            HttpServletRequest request = attributes.getRequest();
            Object username = request.getAttribute("username");
            if(username != null){
                log.setUsername(username.toString());
            }
            log.setMethod(request.getMethod());
            String ip = request.getHeader("X-Forwarded-For");
            if(ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)){
                ip = request.getHeader("Proxy-Client-IP");
            }
            if(ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)){
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if(ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)){
                ip = request.getRemoteAddr();
            }
            if(ip != null && ip.contains(",")){
                ip = ip.split(",")[0].trim();
            }
            log.setIp(ip);
        }

        operationLogMapper.insert(log);
    }
}
