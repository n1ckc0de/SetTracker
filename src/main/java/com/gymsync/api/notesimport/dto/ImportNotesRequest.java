package com.gymsync.api.notesimport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImportNotesRequest(@NotBlank @Size(max = 10000) String rawText) {}
