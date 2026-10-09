package com.charging.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志实体类 (无 deleted 字段)
 */
@Data
@TableName("t_login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户类型: 1-管理员, 2-小程序用户
     */
    private Integer userType;

    private Long userId;

    private String username;

    private String loginIp;

    private String loginLocation;

    private String browser;

    private String os;

    /**
     * 状态: 0-失败, 1-成功
     */
    private Integer status;

    private String msg;

    private LocalDateTime loginTime;
}
