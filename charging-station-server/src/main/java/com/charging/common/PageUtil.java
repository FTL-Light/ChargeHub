package com.charging.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数兼容工具
 *
 * <p>前端历史版本使用 page/size，后端接口使用 current/size。为避免两端再次漂移，
 * 所有分页接口同时接受 current 与 page 两个参数，current 优先。
 */
public final class PageUtil {

    /**
     * 单页最大条数(与 MybatisPlusConfig 中的 maxLimit 保持一致)
     */
    public static final int MAX_PAGE_SIZE = 500;

    private static final int DEFAULT_PAGE_SIZE = 10;

    private PageUtil() {
    }

    /**
     * 构建分页对象，兼容 current / page 两种页码参数
     *
     * @param current 页码(后端标准参数)
     * @param page    页码(前端历史参数)
     * @param size    每页条数
     * @return 分页对象
     */
    public static <T> Page<T> of(Integer current, Integer page, Integer size) {
        int pageNo = current != null ? current : (page != null ? page : 1);
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        if (pageNo < 1) {
            pageNo = 1;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        return new Page<>(pageNo, pageSize);
    }
}
