package com.gymsync.api.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VolumeTrendResponse(int weeks, List<WeeklyVolumePoint> points) {

  public record WeeklyVolumePoint(LocalDate weekStart, LocalDate weekEnd, BigDecimal totalVolume) {}
}
