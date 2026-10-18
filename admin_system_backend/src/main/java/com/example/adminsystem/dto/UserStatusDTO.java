package com.example.adminsystem.dto;

import lombok.Data;

@Data
public class UserStatusDTO {
    /** 用户id */
    private Long id;

    /** 启用状态：1启用，0禁用 */
    private Integer enabled;
}
