package com.jiber.backend.notice;

import com.jiber.backend.common.PageMetadata;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NoticeService {

    public NoticeListResponse listNotices(NoticeListRequest request) {
        return new NoticeListResponse(List.of(), PageMetadata.empty(request.effectivePage(), request.effectiveSize()));
    }

    public NoticeDetailResponse getNotice(Long noticeId) {
        var now = OffsetDateTime.now();
        return new NoticeDetailResponse(
                noticeId,
                "서비스 안내",
                "공지사항 상세 스켈레톤 응답입니다.",
                false,
                now,
                now,
                now
        );
    }

    public NoticeMutationResponse createNotice(NoticeUpsertRequest request) {
        return new NoticeMutationResponse(0L, "공지사항을 등록했습니다.");
    }

    public NoticeMutationResponse updateNotice(Long noticeId, NoticeUpsertRequest request) {
        return new NoticeMutationResponse(noticeId, "공지사항을 수정했습니다.");
    }

    public NoticeMutationResponse deleteNotice(Long noticeId) {
        return new NoticeMutationResponse(noticeId, "공지사항을 삭제했습니다.");
    }
}
