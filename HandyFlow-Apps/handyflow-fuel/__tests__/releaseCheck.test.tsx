import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ReleaseCheckScreen } from "@overview/ReleaseCheckScreen";

const mockApi = jest.fn();
let mockPermissions = ["FUEL_ARRIVAL_CHECK_OVERRIDE"];
class MockApiError extends Error { constructor(m: string, readonly status: number) { super(m); } get isNetwork() { return this.status === 0; } }
jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a), get ApiError() { return MockApiError; } }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { permissions: mockPermissions } }) }));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View>, styles: {} };
});

const hoursAgo = (h: number) => new Date(Date.now() - h * 3_600_000).toISOString();
const stale = (o: any = {}) => ({ deliveryId: "L1", allowed: true, findings: [], checkedAt: hoursAgo(20), status: "STALE", freshHours: 12, ...o });
const props = { route: { params: { loadId: "L1" } } } as any;

beforeEach(() => { mockApi.mockReset(); mockPermissions = ["FUEL_ARRIVAL_CHECK_OVERRIDE"]; });

describe("ReleaseCheckScreen", () => {
  it("does not load or offer anything without the permission", async () => {
    mockPermissions = ["FUEL_MANAGE"];
    await render(<ReleaseCheckScreen {...props} />);
    expect(screen.getByText(/do not have permission/)).toBeTruthy();
    expect(mockApi).not.toHaveBeenCalled();
  });

  it("releases a stale check with a reason and an explanation", async () => {
    mockApi.mockResolvedValueOnce(stale());
    mockApi.mockResolvedValueOnce(stale({ status: "RELEASED", expiresAt: new Date(Date.now() + 3_600_000).toISOString() }));
    await render(<ReleaseCheckScreen {...props} />);
    await screen.findByLabelText("Waited in the site queue");
    await fireEvent.press(screen.getByLabelText("Waited in the site queue"));
    await fireEvent.changeText(screen.getByLabelText("Explain the release"), "Truck queued at the gate after the check.");
    await fireEvent.press(screen.getByText("Release the check"));
    await waitFor(() => expect(screen.getByText(/Released\./)).toBeTruthy());
    expect(mockApi).toHaveBeenLastCalledWith("/api/v1/fuel/deliveries/L1/arrival-check/release", { method: "POST", body: { reasonCode: "SITE_QUEUE", explanation: "Truck queued at the gate after the check." } });
  });

  it("asks for a reason and a real explanation before sending anything", async () => {
    mockApi.mockResolvedValueOnce(stale());
    await render(<ReleaseCheckScreen {...props} />);
    await screen.findByText("Release the check");
    await fireEvent.press(screen.getByText("Release the check"));
    expect(mockApi).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("alert")).toBeTruthy();
  });

  it("offers no release for a blocked check", async () => {
    mockApi.mockResolvedValueOnce(stale({ allowed: false, status: "BLOCKED", findings: [{ code: "X", severity: "BLOCK", message: "Tanker not rated for this product." }] }));
    await render(<ReleaseCheckScreen {...props} />);
    expect(await screen.findByText(/cannot be released/)).toBeTruthy();
    expect(screen.queryByText("Release the check")).toBeNull();
  });

  it("shows the server's refusal and does not claim a release", async () => {
    mockApi.mockResolvedValueOnce(stale());
    mockApi.mockRejectedValueOnce(new Error("The arrival check no longer passes: Driver is suspended."));
    await render(<ReleaseCheckScreen {...props} />);
    await fireEvent.press(await screen.findByLabelText("Waited in the site queue"));
    await fireEvent.changeText(screen.getByLabelText("Explain the release"), "Truck queued at the gate after the check.");
    await fireEvent.press(screen.getByText("Release the check"));
    expect(await screen.findByText(/no longer passes/)).toBeTruthy();
    expect(screen.queryByText(/Released\./)).toBeNull();
  });
});
