package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.Notice;
import com.charging.mapper.NoticeMapper;
import com.charging.service.NoticeService;
import org.springframework.stereotype.Service;

/**
 * 公告服务实现类
 */
@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {
}
