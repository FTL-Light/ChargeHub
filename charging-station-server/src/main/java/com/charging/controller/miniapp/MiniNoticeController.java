package com.charging.controller.miniapp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.PageUtil;
import com.charging.common.exception.BusinessException;
import com.charging.common.result.R;
import com.charging.entity.Notice;
import com.charging.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 小程序端-公告控制器
 */
@Slf4j
@RestController
@RequestMapping("/miniapp/notice")
@RequiredArgsConstructor
@Tag(name = "小程序端-公告", description = "公告相关接口")
public class MiniNoticeController {

    private final NoticeService noticeService;

    @GetMapping("/list")
    @Operation(summary = "分页查询公告列表")
    public R<Page<Notice>> list(
            @Parameter(description = "当前页") @RequestParam(required = false) Integer current,
            @Parameter(description = "页码(兼容前端 page 参数)") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "类型") @RequestParam(required = false) Integer type) {
        Page<Notice> pageResult = PageUtil.of(current, page, size);
        return R.ok(noticeService.pageCached((int) pageResult.getCurrent(), (int) pageResult.getSize(), type));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取公告详情")
    public R<Notice> detail(@Parameter(description = "公告ID") @PathVariable Long id) {
        Notice notice = noticeService.getById(id);
        if (notice == null) {
            throw new BusinessException("公告不存在");
        }
        return R.ok(notice);
    }
}
