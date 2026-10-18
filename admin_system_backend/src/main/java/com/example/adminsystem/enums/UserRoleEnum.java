package com.example.adminsystem.enums;

import lombok.Getter;

@Getter
public enum UserRoleEnum {
    EMPLOYEE(0, "普通员工"),
    ADMIN(1, "管理员");

    private final Integer code;
    private final String desc;

    UserRoleEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
