import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { OpenShiftScreen } from "@forecourt/OpenShiftScreen";
import { CloseShiftScreen } from "@forecourt/CloseShiftScreen";
import { shiftSchema } from "@forecourt/shifts";

const mockApi = jest.fn();
const mockGoBack = jest.fn();
const mockRefresh = jest.fn(async () => undefined);
let mockPumps: any[] = [];
let mockShift: any = null;
class MockApiError extends Error { constructor(m: string, readonly status: number) { super(m); } get isNetwork() { return this.status === 0; } }

jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a), get ApiError() { return MockApiError; } }));
jest.mock("@react-navigation/native", () => ({ useNavigation: () => ({ goBack: mockGoBack, navigate: jest.fn() }) }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { firstName: "Thandi", lastName: "M", email: "t@x", permissions: ["FUEL_FORECOURT"] } }) }));
jest.mock("@forecourt/hooks", () => ({
  usePumps: () => ({ pumps: mockPumps, isLoading: false, error: null }),
  useShift: () => ({ shift: mockShift, isLoading: false, error: null }),
  useRefreshShifts: () => mockRefresh,
  failText: (e: any) => (e?.status === 0 ? "No signal. Nothing was saved. Try again when you have signal." : e.message),
}));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View> };
});

beforeEach(() => {
  mockApi.mockReset(); mockGoBack.mockClear(); mockRefresh.mockClear();
  mockPumps = [{ id: "P1", name: "Pump 1", fuelType: "DIESEL_50", lastMeter: 1000, pricePerLitre: 20, active: true, onShift: false }, { id: "P2", name: "Pump 2", fuelType: "ULP95", lastMeter: 500, pricePerLitre: 22, active: true, onShift: true }];
  mockShift = shiftSchema.parse({ id: "S1", attendantName: "Thandi", status: "OPEN", openedAt: "2026-11-18T06:00:00Z", readings: [{ pumpId: "P1", pumpName: "Pump 1", openingMeter: 1000 }] });
});

describe("OpenShiftScreen", () => {
  it("fills the last meter in for free pumps only, and opens the shift", async () => {
    mockApi.mockResolvedValue({});
    await render(<OpenShiftScreen />);
    expect(screen.queryByLabelText(/Opening meter: Pump 2/)).toBeNull();
    expect(screen.getByDisplayValue("1000")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Open shift"));
    await waitFor(() => expect(mockGoBack).toHaveBeenCalled());
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts", { method: "POST", body: { attendantName: "Thandi M", readings: [{ pumpId: "P1", openingMeter: 1000 }] } });
    expect(mockRefresh).toHaveBeenCalled();
  });
  it("shows the server's refusal and stays on the screen", async () => {
    mockApi.mockRejectedValue(new MockApiError("Already on an open shift: Pump 1. Close that shift first.", 409));
    await render(<OpenShiftScreen />);
    await fireEvent.press(screen.getByLabelText("Open shift"));
    await waitFor(() => expect(screen.getByText(/Already on an open shift/)).toBeTruthy());
    expect(mockGoBack).not.toHaveBeenCalled();
  });
});

describe("CloseShiftScreen", () => {
  it("refuses a closing meter below the opening meter without calling the server", async () => {
    await render(<CloseShiftScreen {...({ route: { params: { shiftId: "S1" } } } as any)} />);
    await fireEvent.changeText(screen.getByLabelText(/Closing meter: Pump 1/), "900");
    await fireEvent.press(screen.getByLabelText("Close shift"));
    expect(screen.getByText(/below its opening meter/)).toBeTruthy();
    expect(mockApi).not.toHaveBeenCalled();
  });
  it("shows litres from the meters, then closes with the takings", async () => {
    mockApi.mockResolvedValue({});
    await render(<CloseShiftScreen {...({ route: { params: { shiftId: "S1" } } } as any)} />);
    await fireEvent.changeText(screen.getByLabelText(/Closing meter: Pump 1/), "1100");
    expect(screen.getByText(/100 L · about R/)).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Cash taken (rand)"), "1500");
    await fireEvent.changeText(screen.getByLabelText("Card taken (rand)"), "500");
    await fireEvent.press(screen.getByLabelText("Close shift"));
    await waitFor(() => expect(mockGoBack).toHaveBeenCalled());
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/S1/close", { method: "POST", body: { cash: 1500, card: 500, account: 0, other: 0, fleetCard: 0, readings: [{ pumpId: "P1", closingMeter: 1100 }] } });
  });
  it("says no signal and does not go back", async () => {
    mockApi.mockRejectedValue(new MockApiError("x", 0));
    await render(<CloseShiftScreen {...({ route: { params: { shiftId: "S1" } } } as any)} />);
    await fireEvent.changeText(screen.getByLabelText(/Closing meter: Pump 1/), "1100");
    await fireEvent.press(screen.getByLabelText("Close shift"));
    await waitFor(() => expect(screen.getByText(/Nothing was saved/)).toBeTruthy());
    expect(mockGoBack).not.toHaveBeenCalled();
  });
  it("will not close a shift that is already closed", async () => {
    mockShift = { ...mockShift, status: "CLOSED" };
    await render(<CloseShiftScreen {...({ route: { params: { shiftId: "S1" } } } as any)} />);
    expect(screen.getByText(/already closed/)).toBeTruthy();
  });
});
