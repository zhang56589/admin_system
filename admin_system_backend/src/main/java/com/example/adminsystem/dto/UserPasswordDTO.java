package com.example.adminsystem.dto;

import lombok.Data;

@Data
public class UserPasswordDTO {
    /** 用户id */
    private Long id;

    /** 新密码（明文，后端BCrypt加密后入库） */
    private String password;
}
