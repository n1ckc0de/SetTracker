package com.gymsync.api.program.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.program.dto.CreateProgramDayRequest;
import com.gymsync.api.program.dto.CreateProgramRequest;
import com.gymsync.api.program.dto.ProgramDetailResponse;
import com.gymsync.api.program.dto.ProgramSummaryResponse;
import com.gymsync.api.program.dto.StartProgramDayResponse;
import com.gymsync.api.program.dto.UpdateProgramDayRequest;
import com.gymsync.api.program.dto.UpdateProgramRequest;
import com.gymsync.api.program.service.ProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/programs")
@Tag(name = "Programs", description = "User-owned training program builder")
public class ProgramController {

  private final ProgramService programService;

  public ProgramController(ProgramService programService) {
    this.programService = programService;
  }

  @GetMapping
  @Operation(summary = "List programs owned by the current user")
  public List<ProgramSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
    return programService.listPrograms(principal.getId());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a program")
  public ProgramSummaryResponse create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateProgramRequest request) {
    return programService.createProgram(principal.getId(), request);
  }

  @GetMapping("/{programId}")
  @Operation(summary = "Get a program with days and planned exercises")
  public ProgramDetailResponse get(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID programId) {
    return programService.getProgram(principal.getId(), programId);
  }

  @PutMapping("/{programId}")
  @Operation(summary = "Update program name/description")
  public ProgramSummaryResponse update(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID programId,
      @Valid @RequestBody UpdateProgramRequest request) {
    return programService.updateProgram(principal.getId(), programId, request);
  }

  @DeleteMapping("/{programId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Hard-delete a program and its days (planning data only)")
  public void delete(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID programId) {
    programService.deleteProgram(principal.getId(), programId);
  }

  @PostMapping("/{programId}/days")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a day with planned exercises")
  public ProgramDetailResponse.ProgramDayResponse addDay(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID programId,
      @Valid @RequestBody CreateProgramDayRequest request) {
    return programService.addDay(principal.getId(), programId, request);
  }

  @PutMapping("/{programId}/days/{dayNumber}")
  @Operation(summary = "Replace a day's name and planned exercises")
  public ProgramDetailResponse.ProgramDayResponse updateDay(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID programId,
      @PathVariable int dayNumber,
      @Valid @RequestBody UpdateProgramDayRequest request) {
    return programService.updateDay(principal.getId(), programId, dayNumber, request);
  }

  @DeleteMapping("/{programId}/days/{dayNumber}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a program day")
  public void deleteDay(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID programId,
      @PathVariable int dayNumber) {
    programService.deleteDay(principal.getId(), programId, dayNumber);
  }

  @PostMapping("/{programId}/days/{dayNumber}/start")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Start an active workout guided by a program day",
      description =
          "Creates a MANUAL ACTIVE workout (same one-active-workout rules as POST /api/workouts). "
              + "Does not fabricate WorkoutSet rows — returns planned exercises as guidance only.")
  public StartProgramDayResponse startDay(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID programId,
      @PathVariable int dayNumber) {
    return programService.startDay(principal.getId(), programId, dayNumber);
  }
}
