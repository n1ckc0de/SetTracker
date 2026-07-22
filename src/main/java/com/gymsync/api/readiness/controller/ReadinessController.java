package com.gymsync.api.readiness.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.readiness.dto.CreateReadinessRequest;
import com.gymsync.api.readiness.dto.ReadinessResponse;
import com.gymsync.api.readiness.service.ReadinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/readiness")
@Tag(name = "Readiness", description = "Pre-workout readiness check-ins")
public class ReadinessController {

  private final ReadinessService readinessService;

  public ReadinessController(ReadinessService readinessService) {
    this.readinessService = readinessService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a readiness check-in (all fields optional)")
  public ReadinessResponse create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateReadinessRequest request) {
    return readinessService.create(principal.getId(), request);
  }

  @GetMapping("/latest")
  @Operation(summary = "Get the most recent readiness check-in")
  public ReadinessResponse latest(@AuthenticationPrincipal UserPrincipal principal) {
    return readinessService.latest(principal.getId());
  }

  @GetMapping
  @Operation(summary = "List readiness check-ins in a date range (defaults to last 30 days)")
  public List<ReadinessResponse> list(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return readinessService.list(principal.getId(), from, to);
  }
}
