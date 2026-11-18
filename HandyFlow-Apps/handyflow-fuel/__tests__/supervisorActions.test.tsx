import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { SupervisorActions } from "@forecourt/SupervisorActions";
import { adjustmentSchema, canReverse, canSupervise, reasonProblem, shiftSchema } from "@forecourt/shifts";

const mockApi = jest.fn();
const mockRefresh = jest.fn(async () => undefined);
class MockApiError extends Error { constructor(m: string, readonly status: number) { super(m); } get isNetwork() { return this.status === 0; } }

jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a), get ApiError() { return MockApiError; } }));
jest.mock("@forecourt/hooks", () => ({
  SUPERVISOR_BASE: "/api/v1/fuel/forecourt",
  useRefreshShifts: () => mockRefresh,
  failText: (e: any) => (e?.status === 0 ? "No signal. Nothing was saved. Try again when you have signal." : e.message),
}));
jest.mock("@/ui/Screen", () => {
  const { View } = require("react-native");
  return { Card: ({ children }: any) => <View>{children}</View> };
});

const shift = (o: any = {}) => shiftSchema.parse({ id: "S1", attendantName: "Thandi", status: "CLOSED", openedAt: "2026-11-18T06:00:00Z", closedAt: "2026-11-18T14:00:00Z", readings: [], reviewStatus: "PENDING_REVIEW", ...o });
const adj = (o: any = {}) => adjustmentSchema.parse({ id: "A1", kind: "REFUND", amount: 150, reason: "Wrong pump", status: "PENDING", ...o });
const SUP = ["FUEL_SHIFT_SUPERVISE"];
beforeEach(() => { mockApi.mockReset(); mockRefresh.mockClear(); });

describe("permissions", () => {
  it("supervise and reverse are separate", () => {
    expect(canSupervise(["FUEL_SHIFT_SUPERVISE"])).toBe(true);
    expect(canSupervise(["FUEL_MANAGE"])).toBe(false);
    expect(canReverse(["FUEL_FORECOURT_MANAGE"])).toBe(true);
    expect(canReverse(["FUEL_SHIFT_SUPERVISE"])).toBe(false);
  });
  it("a reason must be 5 to the limit", () => {
    expect(reasonProblem("abc")).toMatch(/at least 5/);
    expect(reasonProblem("x".repeat(301))).toMatch(/300/);
    expect(reasonProblem("x".repeat(400), 500)).toBeNull();
    expect(reasonProblem("Counted twice")).toBeNull();
  });
});

describe("SupervisorActions", () => {
  it("shows nothing to someone without the permissions", async () => {
    await render(<SupervisorActions shift={shift()} adjustments={[adj()]} permissions={["FUEL_FORECOURT"]} />);
    expect(screen.queryByText("Review this shift")).toBeNull();
    expect(screen.queryByText(/Approve/)).toBeNull();
    expect(screen.queryByText("Reverse this shift")).toBeNull();
  });

  it("approves a pending adjustment with no typing, online only", async () => {
    mockApi.mockResolvedValue({});
    await render(<SupervisorActions shift={shift()} adjustments={[adj()]} permissions={SUP} />);
    await fireEvent.press(screen.getByText(/Approve refund/));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/adjustments/A1/approve", { method: "POST" }));
    expect(mockRefresh).toHaveBeenCalledWith("S1");
  });

  it("rejects only with a reason", async () => {
    mockApi.mockResolvedValue({});
    await render(<SupervisorActions shift={shift()} adjustments={[adj()]} permissions={SUP} />);
    await fireEvent.press(screen.getByText(/Reject refund/));
    await fireEvent.press(screen.getByText("Confirm"));
    expect(mockApi).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Customer was not refunded");
    await fireEvent.press(screen.getByText("Confirm"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/adjustments/A1/reject", { method: "POST", body: { reason: "Customer was not refunded" } }));
  });

  it("offers no review for a shift that was not outside tolerance", async () => {
    await render(<SupervisorActions shift={shift({ reviewStatus: "NONE" })} adjustments={[]} permissions={SUP} />);
    expect(screen.queryByText("Review this shift")).toBeNull();
  });

  it("reviews a shift that closed outside tolerance", async () => {
    mockApi.mockResolvedValue({});
    await render(<SupervisorActions shift={shift()} adjustments={[]} permissions={SUP} />);
    await fireEvent.press(screen.getByText("Review this shift"));
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Short cash explained by a till error");
    await fireEvent.press(screen.getByText("Confirm"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/S1/review", { method: "POST", body: { reason: "Short cash explained by a till error" } }));
  });

  it("reverses a closed shift with a reason, for a manager", async () => {
    mockApi.mockResolvedValue({});
    await render(<SupervisorActions shift={shift({ reviewStatus: "NONE" })} adjustments={[]} permissions={["FUEL_FORECOURT_MANAGE"]} />);
    await fireEvent.press(screen.getByText("Reverse this shift"));
    expect(screen.getByText(/no longer counts/)).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Posted to the wrong site");
    await fireEvent.press(screen.getByText("Confirm"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/S1/reverse", { method: "POST", body: { reason: "Posted to the wrong site" } }));
  });

  it("offers no reversal on an open shift", async () => {
    await render(<SupervisorActions shift={shift({ status: "OPEN", reviewStatus: "NONE" })} adjustments={[]} permissions={["FUEL_FORECOURT_MANAGE"]} />);
    expect(screen.queryByText("Reverse this shift")).toBeNull();
  });

  it("shows the server's refusal and does not say it was saved", async () => {
    mockApi.mockRejectedValue(new Error("This shift is not waiting for review."));
    await render(<SupervisorActions shift={shift()} adjustments={[]} permissions={SUP} />);
    await fireEvent.press(screen.getByText("Review this shift"));
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Looks fine to me");
    await fireEvent.press(screen.getByText("Confirm"));
    expect(await screen.findByText("This shift is not waiting for review.")).toBeTruthy();
    expect(screen.queryByText("Saved.")).toBeNull();
  });

  it("says plainly when there is no signal and saves nothing", async () => {
    mockApi.mockRejectedValue(new MockApiError("net", 0));
    await render(<SupervisorActions shift={shift()} adjustments={[adj()]} permissions={SUP} />);
    await fireEvent.press(screen.getByText(/Approve refund/));
    expect(await screen.findByText(/No signal. Nothing was saved/)).toBeTruthy();
  });
});
