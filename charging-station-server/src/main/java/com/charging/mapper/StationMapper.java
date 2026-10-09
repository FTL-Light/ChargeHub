package com.charging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charging.entity.Station;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 充电站 Mapper
 */
@Mapper
public interface StationMapper extends BaseMapper<Station> {

    /**
     * 根据自定义 SQL 查询站点
     *
     * @param sql 查询SQL
     * @return 站点列表
     */
    @Select("${sql}")
    List<Station> selectBySql(String sql);
}
