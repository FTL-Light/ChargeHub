package com.charging.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.charging.entity.AiChatLog;

import java.util.List;

/**
 * AI 对话日志服务接口
 */
public interface AiChatLogService extends IService<AiChatLog> {

    /**
     * 获取用户最近对话历史
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param limit     限制条数
     * @return 对话历史列表
     */
    List<AiChatLog> getRecentHistory(Long userId, String sessionId, int limit);
}
