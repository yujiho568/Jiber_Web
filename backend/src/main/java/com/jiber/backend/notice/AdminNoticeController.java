package com.jiber.backend.notice;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping
    public NoticeMutationResponse createNotice(@Valid @RequestBody NoticeUpsertRequest request) {
        return noticeService.createNotice(request);
    }

    @PutMapping("/{noticeId}")
    public NoticeMutationResponse updateNotice(
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeUpsertRequest request
    ) {
        return noticeService.updateNotice(noticeId, request);
    }

    @DeleteMapping("/{noticeId}")
    public NoticeMutationResponse deleteNotice(@PathVariable Long noticeId) {
        return noticeService.deleteNotice(noticeId);
    }
}
