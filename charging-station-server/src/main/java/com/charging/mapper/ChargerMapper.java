package com.charging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charging.entity.Charger;
import org.apache.ibatis.annotations.Mapper;

/**
 * 充电桩 Mapper
 */
@Mapper
public interface ChargerMapper extends BaseMapper<Charger> {
}
