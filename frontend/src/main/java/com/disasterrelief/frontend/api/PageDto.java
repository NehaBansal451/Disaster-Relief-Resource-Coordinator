package com.disasterrelief.frontend.api;

import java.util.List;

/** Backend list envelope: {content[], page, size, totalElements, totalPages}. */
public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
