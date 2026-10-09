package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.Charger;
import com.charging.mapper.ChargerMapper;
import com.charging.service.ChargerService;
import org.springframework.stereotype.Service;

/**
 * 充电桩服务实现类
 */
@Service
public class ChargerServiceImpl extends ServiceImpl<ChargerMapper, Charger> implements ChargerService {
}
