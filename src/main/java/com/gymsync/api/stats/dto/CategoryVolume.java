package com.gymsync.api.stats.dto;

import com.gymsync.api.exercise.domain.ExerciseCategory;
import java.math.BigDecimal;

public record CategoryVolume(ExerciseCategory category, BigDecimal volume) {}
