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
 * 充电桩实体类
 */
@Data
@TableName("t_charger")
public class Charger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;

    private String code;

    private String name;

    /**
     * 类型: 1-慢充, 2-快充
     */
    private Integer type;

    private BigDecimal power;

    private Integer gunCount;

    private String image;

    /**
     * 状态: 0-故障, 1-空闲, 2-使用中
     */
    private Integer status;

    private BigDecimal price;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
