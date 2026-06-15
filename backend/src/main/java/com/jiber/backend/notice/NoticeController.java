package com.jiber.backend.notice;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public NoticeListResponse listNotices(@Valid @ParameterObject @ModelAttribute NoticeListRequest request) {
        return noticeService.listNotices(request);
    }

    @GetMapping("/{noticeId}")
    public NoticeDetailResponse getNotice(@PathVariable Long noticeId) {
        return noticeService.getNotice(noticeId);
    }
}
