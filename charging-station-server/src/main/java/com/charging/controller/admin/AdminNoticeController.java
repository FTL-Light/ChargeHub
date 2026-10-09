package com.charging.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.time.LocalDateTime;

/**
 * 管理端-公告控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/notice")
@RequiredArgsConstructor
@Tag(name = "管理端-公告管理", description = "公告相关接口")
public class AdminNoticeController {

    private final NoticeService noticeService;

    @GetMapping("/list")
    @Operation(summary = "分页查询公告列表")
    public R<Page<Notice>> list(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "标题") @RequestParam(required = false) String title,
            @Parameter(description = "类型") @RequestParam(required = false) Integer type,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        Page<Notice> page = new Page<>(current, size);
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(title != null && !title.isEmpty(), Notice::getTitle, title);
        wrapper.eq(type != null, Notice::getType, type);
        wrapper.eq(status != null, Notice::getStatus, status);
        wrapper.orderByDesc(Notice::getTop).orderByDesc(Notice::getPublishTime);
        return R.ok(noticeService.page(page, wrapper));
    }

    @PostMapping
    @Operation(summary = "新增公告")
    public R<Void> add(@RequestBody Notice notice) {
        notice.setId(null);
        if (notice.getPublishTime() == null) {
            notice.setPublishTime(LocalDateTime.now());
        }
        noticeService.save(notice);
        return R.ok();
    }

    @PutMapping
    @Operation(summary = "编辑公告")
    public R<Void> update(@RequestBody Notice notice) {
        if (notice.getId() == null) {
            throw new BusinessException("公告ID不能为空");
        }
        noticeService.updateById(notice);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告")
    public R<Void> delete(@Parameter(description = "公告ID") @PathVariable Long id) {
        noticeService.removeById(id);
        return R.ok();
    }
}
