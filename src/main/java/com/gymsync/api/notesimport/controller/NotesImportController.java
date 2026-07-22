package com.gymsync.api.notesimport.controller;

import com.gymsync.api.auth.security.UserPrincipal;
import com.gymsync.api.notesimport.dto.ImportNotesRequest;
import com.gymsync.api.notesimport.service.NotesImportService;
import com.gymsync.api.workout.dto.WorkoutDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-assisted workout-notes import. Parses free-text notes (via a rule-based parser locally, or an
 * optional configured LLM provider) into a {@code PENDING_REVIEW} workout that the user must
 * explicitly confirm (see {@code WorkoutController#confirmImport}) before it counts toward official
 * statistics. See docs/AI_NOTES_IMPORT.md.
 */
@RestController
@RequestMapping("/api/notes")
@Tag(name = "Notes Import", description = "AI-assisted import of free-text workout notes")
public class NotesImportController {

  private final NotesImportService notesImportService;

  public NotesImportController(NotesImportService notesImportService) {
    this.notesImportService = notesImportService;
  }

  @PostMapping("/import")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Parse raw workout notes into a pending-review workout for human confirmation")
  public WorkoutDetailResponse importNotes(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ImportNotesRequest request) {
    return notesImportService.importNotes(principal.getId(), request);
  }
}
