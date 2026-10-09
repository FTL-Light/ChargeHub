package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charging.entity.AiChatLog;
import com.charging.mapper.AiChatLogMapper;
import com.charging.service.AiChatLogService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 对话日志服务实现类
 */
@Service
public class AiChatLogServiceImpl extends ServiceImpl<AiChatLogMapper, AiChatLog> implements AiChatLogService {

    /**
     * 根据用户ID和可选的会话ID查询最近的AI对话历史记录
     * 
     * @param userId    用户ID，必填，用于过滤指定用户的对话记录
     * @param sessionId 会话ID，可选，如果不为空则只查询该会话下的记录
     * @param limit     返回记录的最大条数
     * @return 按创建时间倒序排列的对话日志列表
     */
    @Override
    public List<AiChatLog> getRecentHistory(Long userId, String sessionId, int limit) {
        // 创建 Lambda 查询构造器，基于实体类的字段进行类型安全的查询条件构建
        LambdaQueryWrapper<AiChatLog> wrapper = new LambdaQueryWrapper<>();
        
        // 添加条件：只查询指定用户的对话日志（必填条件）
        wrapper.eq(AiChatLog::getUserId, userId);
        
        // 如果传入了会话ID，则额外添加会话ID的过滤条件，实现会话级别的历史查询
        if (sessionId != null && !sessionId.isEmpty()) {
            wrapper.eq(AiChatLog::getSessionId, sessionId);
        }
        
        // 按创建时间倒序排列，使最新的对话记录排在前面
        wrapper.orderByDesc(AiChatLog::getCreateTime);
        
        // 拼接原生 SQL 的 LIMIT 子句，限制返回的记录条数
        wrapper.last("LIMIT " + limit);
        
        // 调用 MyBatis-Plus 提供的 list 方法执行查询并返回结果
        return this.list(wrapper);
    }
}
