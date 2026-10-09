package com.charging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charging.entity.AiChatLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话日志 Mapper
 */
@Mapper
public interface AiChatLogMapper extends BaseMapper<AiChatLog> {
}
