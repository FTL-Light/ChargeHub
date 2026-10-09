package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.Gun;
import com.charging.mapper.GunMapper;
import com.charging.service.GunService;
import org.springframework.stereotype.Service;

/**
 * 充电枪服务实现类
 */
@Service
public class GunServiceImpl extends ServiceImpl<GunMapper, Gun> implements GunService {
}
