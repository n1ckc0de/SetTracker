import { describe, expect, it } from "vitest";
import { getCurrentProgram, setCurrentProgram, clearCurrentProgram } from "./currentProgram";

describe("currentProgram storage", () => {
  it("round-trips program selection", () => {
    clearCurrentProgram();
    expect(getCurrentProgram()).toBeNull();
    setCurrentProgram({ id: "p1", name: "Push Pull", dayNumber: 1, dayName: "Push A" });
    const stored = getCurrentProgram();
    expect(stored?.id).toBe("p1");
    expect(stored?.name).toBe("Push Pull");
    expect(stored?.dayNumber).toBe(1);
    clearCurrentProgram();
    expect(getCurrentProgram()).toBeNull();
  });
});
