package com.example.adminsystem.dto;

import lombok.Data;

@Data
public class UserRoleDTO {
    /** 用户id */
    private Long id;

    /** 用户角色 */
    private Integer role;
}
