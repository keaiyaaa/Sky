package com.tianshu.backend.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public PageResponse {
        items = List.copyOf(items);
    }
}
