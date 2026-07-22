package com.gymsync.api.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProgramRequest(
    @NotBlank @Size(max = 255) String name, @Size(max = 2000) String description) {}
