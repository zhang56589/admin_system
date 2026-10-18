package com.example.adminsystem.enums;

import lombok.Getter;

/**
 * 预约状态，与 meeting_booking.status / 实体字段 resStatus 对应。
 * 0待审批，1已通过，2已驳回，3已取消。
 */
@Getter
public enum BookingStatusEnum {
    PENDING(0, "待审批"),
    PASS(1, "已通过"),
    REJECT(2, "已驳回"),
    CANCEL(3, "已取消");

    private final Integer code;
    private final String desc;

    BookingStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
