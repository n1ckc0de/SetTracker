package com.gymsync.api.common.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable, client-friendly pagination envelope. Spring's own {@link Page} serializes with a lot of
 * internal detail (a full {@code pageable} object, {@code sort} metadata, etc.); this record
 * exposes only what API consumers need.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }

  public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
    List<T> mapped = page.getContent().stream().map(mapper).toList();
    return new PageResponse<>(
        mapped,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}
