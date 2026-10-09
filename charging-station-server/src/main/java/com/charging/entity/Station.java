package com.charging.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电站实体类
 */
@Data
@TableName("t_station")
public class Station {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String address;

    private String province;

    private String city;

    private String district;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String image;

    private String contactPhone;

    private String businessHours;

    private Integer chargerCount;

    /**
     * 状态: 0-下线, 1-正常
     */
    private Integer status;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
