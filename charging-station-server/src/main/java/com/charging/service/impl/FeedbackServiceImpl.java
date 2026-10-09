package com.charging.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.Feedback;
import com.charging.mapper.FeedbackMapper;
import com.charging.service.FeedbackService;
import org.springframework.stereotype.Service;

/**
 * 反馈服务实现类
 */
@Service
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {
}
