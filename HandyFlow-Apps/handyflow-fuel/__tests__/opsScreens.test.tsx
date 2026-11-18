import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { PushPreferencesScreen } from "@/screens/PushPreferencesScreen";
import { OfflineConflictsScreen, canSeeConflicts } from "@/screens/OfflineConflictsScreen";
import { isOn, PUSH_TYPES } from "@/pushPrefs";
import { closeWaiting } from "@forecourt/send";
import { queuedOpens, shiftFromQueuedOpen } from "@forecourt/shifts";

const mockApi = jest.fn();
jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a) }));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View> };
});
beforeEach(() => mockApi.mockReset());

describe("PushPreferencesScreen", () => {
  it("shows every alert, on unless the server says off", async () => {
    mockApi.mockResolvedValue({ OVERDUE_DELIVERY: true, DIP_VARIANCE: false });
    await render(<PushPreferencesScreen />);
    expect(await screen.findByText("A tank dip is off: off")).toBeTruthy();
    expect(screen.getByText("A delivery is overdue: on")).toBeTruthy();
    expect(screen.getByText("A shift closed outside tolerance: on")).toBeTruthy(); // not listed: on by default
    expect(PUSH_TYPES.map((t) => t.type)).toEqual(["OVERDUE_DELIVERY", "DIP_VARIANCE", "SHIFT_OUTSIDE_TOLERANCE", "ARRIVAL_CHECK_BLOCKED"]);
  });
  it("switches one alert off with only that alert in the request", async () => {
    mockApi.mockResolvedValueOnce({ OVERDUE_DELIVERY: true }).mockResolvedValueOnce({ OVERDUE_DELIVERY: false });
    await render(<PushPreferencesScreen />);
    await fireEvent.press(await screen.findByLabelText("A delivery is overdue"));
    await waitFor(() => expect(screen.getByText("A delivery is overdue: off")).toBeTruthy());
    expect(mockApi).toHaveBeenLastCalledWith("/api/v1/fuel/push/preferences", { method: "PUT", body: { enabled: { OVERDUE_DELIVERY: false } } });
  });
  it("keeps the old setting and says so when it cannot save", async () => {
    mockApi.mockResolvedValueOnce({ OVERDUE_DELIVERY: true }).mockRejectedValueOnce(new Error("No signal"));
    await render(<PushPreferencesScreen />);
    await fireEvent.press(await screen.findByLabelText("A delivery is overdue"));
    await waitFor(() => expect(screen.getByText("No signal")).toBeTruthy());
    expect(screen.getByText("A delivery is overdue: on")).toBeTruthy();
  });
  it("a type the server did not mention is on", () => expect(isOn({}, "DIP_VARIANCE")).toBe(true));
});

describe("OfflineConflictsScreen", () => {
  const row = { id: "c1", kind: "SHIFT_CLOSE", reason: "This shift is already closed.", createdAt: "2026-11-18T09:00:00Z", capturedAt: "2026-11-18T08:00:00Z", status: "OPEN" };
  it("lists what was refused and why, in words", async () => {
    mockApi.mockResolvedValue([row]);
    await render(<OfflineConflictsScreen />);
    expect(await screen.findByText("Close shift and cash-up")).toBeTruthy();
    expect(screen.getByText("This shift is already closed.")).toBeTruthy();
  });
  it("says so when nothing was refused", async () => {
    mockApi.mockResolvedValue([]);
    await render(<OfflineConflictsScreen />);
    expect(await screen.findByText("Nothing refused.")).toBeTruthy();
  });
  it("marks one as dealt with and reloads", async () => {
    mockApi.mockResolvedValueOnce([row]).mockResolvedValueOnce(true).mockResolvedValueOnce([]);
    await render(<OfflineConflictsScreen />);
    await fireEvent.press(await screen.findByLabelText("Mark as dealt with"));
    await waitFor(() => expect(screen.getByText("Nothing refused.")).toBeTruthy());
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/offline/conflicts/c1/resolve", { method: "POST", body: {} });
  });
  it("only supervisors and managers are offered the list", () => {
    expect(canSeeConflicts(["FUEL_SHIFT_SUPERVISE"])).toBe(true);
    expect(canSeeConflicts(["FUEL_FORECOURT_MANAGE"])).toBe(true);
    expect(canSeeConflicts(["FUEL_DRIVER", "FUEL_READ"])).toBe(false);
  });
});

describe("a shift opened offline", () => {
  const open = { id: "OPEN-1", kind: "SHIFT_OPEN", recordedAt: "2026-11-18T06:00:00.000Z", payload: { body: { attendantName: "Thandi", readings: [{ pumpId: "P1", openingMeter: 1000 }] } } };
  it("is recognised in the queue and turned into a shift to close", () => {
    expect(queuedOpens([open, { ...open, id: "x", kind: "SHIFT_CLOSE" }])).toHaveLength(1);
    const s = shiftFromQueuedOpen(open, [{ id: "P1", name: "Pump 1", fuelType: "DIESEL_50" }])!;
    expect(s.status).toBe("OPEN"); expect(s.shiftNumber).toBeNull();
    expect(s.readings[0]).toMatchObject({ pumpId: "P1", pumpName: "Pump 1", openingMeter: 1000 });
  });
  it("cannot be built from a malformed saved open", () => expect(shiftFromQueuedOpen({ ...open, payload: {} }, [])).toBeNull());
  it("a close already queued for it blocks a second one", () => {
    const close: any = { kind: "SHIFT_CLOSE", payload: { openRequestId: "OPEN-1" } };
    expect(closeWaiting([close], { openRequestId: "OPEN-1" })).toBe(true);
    expect(closeWaiting([close], { openRequestId: "OTHER" })).toBe(false);
    expect(closeWaiting([close], { shiftId: "S1" })).toBe(false);
  });
});
