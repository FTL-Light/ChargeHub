package com.charging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charging.entity.Station;
import org.apache.ibatis.annotations.Mapper;

/**
 * 充电站 Mapper
 */
@Mapper
public interface StationMapper extends BaseMapper<Station> {
}
