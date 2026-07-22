package com.gymsync.api.notesimport.llm;

import java.math.BigDecimal;

public record ParsedSet(String exerciseName, BigDecimal weight, int reps, int setNumber) {}
