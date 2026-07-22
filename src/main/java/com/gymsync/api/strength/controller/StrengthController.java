package com.gymsync.api.strength.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.strength.dto.Estimate1RmRequest;
import com.gymsync.api.strength.dto.Estimate1RmResponse;
import com.gymsync.api.strength.service.Estimated1RmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strength")
@Tag(name = "Strength", description = "Deterministic strength calculations")
public class StrengthController {

  private final Estimated1RmService estimated1RmService;

  public StrengthController(Estimated1RmService estimated1RmService) {
    this.estimated1RmService = estimated1RmService;
  }

  @PostMapping("/estimate-1rm")
  @Operation(summary = "Estimate 1RM from weight and reps using Epley and Brzycki formulas")
  public Estimate1RmResponse estimate(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody Estimate1RmRequest request) {
    var result =
        estimated1RmService.estimate(
            new Estimated1RmService.EstimateRequest(
                request.weight(), request.reps(), request.rir()));
    return Estimate1RmResponse.from(result, request.exerciseName());
  }
}
