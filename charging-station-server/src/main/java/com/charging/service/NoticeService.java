package com.charging.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.charging.entity.Notice;

/**
 * 公告服务接口
 */
public interface NoticeService extends IService<Notice> {

    /**
     * 分页查询公告列表（带缓存：仅缓存无类型筛选的前3页）
     *
     * @param current 当前页
     * @param size    每页条数
     * @param type    公告类型（null 表示不筛选）
     * @return 分页结果
     */
    Page<Notice> pageCached(int current, int size, Integer type);
}
