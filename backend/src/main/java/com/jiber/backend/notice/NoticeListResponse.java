package com.jiber.backend.notice;

import com.jiber.backend.common.PageMetadata;
import java.util.List;

public record NoticeListResponse(
        List<NoticeSummaryResponse> items,
        PageMetadata page
) {
}
