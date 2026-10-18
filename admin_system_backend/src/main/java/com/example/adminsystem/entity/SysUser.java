package com.example.adminsystem.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码，数据库存储BCrypt加密后的密文，禁止明文 */
    @JsonIgnore
    private String password;

    /** 真实姓名 */
    @TableField("real_name")
    private String nickname;

    /** 手机号 */
    private String phone;

    /**
     * 用户角色：0普通员工，1管理员
     * @see com.example.adminsystem.enums.UserRoleEnum
     */
    private Integer role;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
