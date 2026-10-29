package com.eparly.api.offer.admin.dto;

import java.util.List;

public record AdminPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
