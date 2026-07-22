package com.gymsync.api.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModifyCoachRequest(
    @NotBlank @Size(max = 2000) String proposedChange, @Size(max = 2000) String reasoning) {}
