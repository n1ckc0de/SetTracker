package com.gymsync.api.stats.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExerciseVolume(UUID exerciseId, String exerciseName, BigDecimal volume) {}
