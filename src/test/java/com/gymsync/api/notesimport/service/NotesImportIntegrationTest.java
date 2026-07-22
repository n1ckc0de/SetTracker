package com.gymsync.api.notesimport.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymsync.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Full notes-import review lifecycle against the deterministic rule-based parser (the default,
 * credential-free provider — see docs/AI_NOTES_IMPORT.md): import creates a pending-review workout
 * that is excluded from statistics until the user explicitly confirms it.
 */
class NotesImportIntegrationTest extends AbstractIntegrationTest {

  @Test
  void importThenConfirm_countsTowardWeeklyStats_whilePendingItDoesNot() throws Exception {
    RegisteredUser user =
        registerUser("notesimport1@example.com", "supersecret1", "Notes Importer");

    String importResponse =
        mockMvc
            .perform(
                post("/api/notes/import")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"rawText":"Bench 3x8 135 lb"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.importStatus").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.source").value("NOTES_IMPORT"))
            .andExpect(jsonPath("$.sets.length()").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workoutId = objectMapper.readTree(importResponse).get("id").asText();

    // Pending review: must not count toward weekly stats yet.
    mockMvc
        .perform(get("/api/stats/weekly").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedWorkouts").value(0))
        .andExpect(jsonPath("$.totalSets").value(0))
        .andExpect(jsonPath("$.totalVolume").value(0));

    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/confirm-import")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.importStatus").value("CONFIRMED"));

    // Confirmed: now counts.
    mockMvc
        .perform(get("/api/stats/weekly").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedWorkouts").value(1))
        .andExpect(jsonPath("$.totalSets").value(3))
        .andExpect(jsonPath("$.totalReps").value(24))
        .andExpect(jsonPath("$.totalVolume").value(3240));
  }

  @Test
  void rejectImport_doesNotCountTowardStatistics() throws Exception {
    RegisteredUser user =
        registerUser("notesimport2@example.com", "supersecret1", "Notes Rejecter");

    String importResponse =
        mockMvc
            .perform(
                post("/api/notes/import")
                    .header("Authorization", user.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"rawText":"Squat 5x5 185 lb"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workoutId = objectMapper.readTree(importResponse).get("id").asText();

    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/reject-import")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.importStatus").value("REJECTED"));

    mockMvc
        .perform(get("/api/stats/weekly").header("Authorization", user.authorizationHeader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedWorkouts").value(0))
        .andExpect(jsonPath("$.totalSets").value(0));

    // A rejected import can no longer be confirmed.
    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/confirm-import")
                .header("Authorization", user.authorizationHeader()))
        .andExpect(status().isConflict());
  }

  @Test
  void importNotes_blankRawText_returns400() throws Exception {
    RegisteredUser user = registerUser("notesimport3@example.com", "supersecret1", "Notes Blank");

    mockMvc
        .perform(
            post("/api/notes/import")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rawText":""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void importNotes_unparsableText_returns400() throws Exception {
    RegisteredUser user =
        registerUser("notesimport4@example.com", "supersecret1", "Notes Unparsable");

    mockMvc
        .perform(
            post("/api/notes/import")
                .header("Authorization", user.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rawText":"this is not a workout at all"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirmImport_forAnotherUsersWorkout_returns404() throws Exception {
    RegisteredUser owner = registerUser("notesimport5@example.com", "supersecret1", "Notes Owner");
    RegisteredUser other = registerUser("notesimport6@example.com", "supersecret1", "Notes Other");

    String importResponse =
        mockMvc
            .perform(
                post("/api/notes/import")
                    .header("Authorization", owner.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"rawText":"Deadlift 225x5"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workoutId = objectMapper.readTree(importResponse).get("id").asText();

    mockMvc
        .perform(
            patch("/api/workouts/" + workoutId + "/confirm-import")
                .header("Authorization", other.authorizationHeader()))
        .andExpect(status().isNotFound());
  }
}
