package com.gymsync.api.notesimport.llm;

public interface WorkoutNotesParser {
  ParsedWorkout parse(String rawText);
}
