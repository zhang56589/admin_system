package com.example.adminsystem.enums;

import lombok.Getter;

@Getter
public enum RoomStatusEnum {
    DISABLE(0, "不可用"),
    ENABLE(1, "可用");

    private final Integer code;
    private final String desc;

    RoomStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
