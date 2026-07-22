import { describe, expect, it } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { useState } from "react";
import {
  SessionRestTimerProvider,
  useSessionRestTimer,
} from "./SessionRestTimerContext";

function TimerReadout() {
  const { restRemaining, startRest } = useSessionRestTimer();
  return (
    <div>
      <span data-testid="remaining">{restRemaining}</span>
      <button type="button" onClick={() => startRest("WORKING")}>
        Start
      </button>
    </div>
  );
}

function ToggleMount() {
  const [mounted, setMounted] = useState(true);
  return (
    <SessionRestTimerProvider>
      {mounted ? <TimerReadout /> : null}
      <button type="button" onClick={() => setMounted((value) => !value)}>
        Toggle
      </button>
    </SessionRestTimerProvider>
  );
}

describe("SessionRestTimerProvider", () => {
  it("keeps rest timer state when child unmounts and remounts", () => {
    const container = document.createElement("div");
    let root: Root | null = null;

    act(() => {
      root = createRoot(container);
      root.render(<ToggleMount />);
    });

    const buttons = () => Array.from(container.querySelectorAll("button"));
    const remaining = () =>
      Number(container.querySelector("[data-testid='remaining']")?.textContent ?? "0");

    act(() => {
      buttons()[0]?.click();
    });
    expect(remaining()).toBeGreaterThan(0);

    act(() => {
      buttons()[1]?.click();
      buttons()[1]?.click();
    });
    expect(remaining()).toBeGreaterThan(0);

    act(() => {
      root?.unmount();
    });
  });
});
