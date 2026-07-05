package com.wushi.module.review.model;

public record CorrectionFieldItem(
        String fieldName,
        String oldValue,
        String newValue,
        String fieldDesc
) {
}
