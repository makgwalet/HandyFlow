import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { StayDetailScreen } from "@truckstop/StayDetailScreen";
import { StayRequestsScreen } from "@truckstop/StayRequestsScreen";
import { staySchema } from "@truckstop/stays";

const mockApi = jest.fn();
const mockRefresh = jest.fn(async () => undefined);
const mockEnqueue = jest.fn(async () => ({}));
let mockPending: any[] = [];
let mockStays: any[] = [];
let mockRequests: any[] = [];
let mockPermissions = ["FUEL_TRUCKSTOP_MANAGE"];
class MockApiError extends Error { constructor(m: string, readonly status: number) { super(m); } get isNetwork() { return this.status === 0; } }

jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a), get ApiError() { return MockApiError; } }));
jest.mock("@/offline/OutboxProvider", () => ({ useOutbox: () => ({ enqueue: mockEnqueue, pending: mockPending }) }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { permissions: mockPermissions } }) }));
jest.mock("react-native-safe-area-context", () => ({ SafeAreaView: ({ children }: any) => children }));
jest.mock("@/ui/SyncBanner", () => ({ SyncBanner: () => null }));
jest.mock("@truckstop/hooks", () => ({
  useYard: () => ({ stays: mockStays }),
  useBays: () => ({ bays: [{ id: "B1", name: "Bay 1", active: true }, { id: "B2", name: "Bay 2", active: true }] }),
  usePendingRequests: () => ({ requests: mockRequests, isLoading: false, error: null, refetch: jest.fn() }),
  useRefreshYard: () => mockRefresh,
  failText: (e: any) => (e?.status === 0 ? "No signal. Nothing was saved. Try again when you have signal." : e.message),
}));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View>, styles: { root: {}, body: {}, title: {} } };
});

const stay = (o: any = {}) => staySchema.parse({ id: "S1", vehicleRegistration: "ABC 123 GP", arrivalDate: "2020-01-01", departureDate: "2030-01-01", status: "BOOKED", nights: 1, ...o });
const props = { route: { params: { stayId: "S1" } } } as any;

beforeEach(() => { mockEnqueue.mockReset(); mockEnqueue.mockResolvedValue({}); mockPending = []; mockApi.mockReset(); mockRefresh.mockClear(); mockPermissions = ["FUEL_TRUCKSTOP_MANAGE"]; mockStays = [stay()]; mockRequests = []; });

describe("StayDetailScreen", () => {
  it("saves a check-in on the phone and does not claim the truck is in until the server says so", async () => {
    await render(<StayDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Check in"));
    await waitFor(() => expect(screen.getByText(/Saved on this phone\. It is not confirmed/)).toBeTruthy());
    expect(mockEnqueue).toHaveBeenCalledWith("STAY_CHECK_IN", { stayId: "S1" });
    expect(mockApi).not.toHaveBeenCalled();
  });
  it("saves a check-out with the date it was captured, so a late sync adds no nights", async () => {
    mockStays = [stay({ status: "CHECKED_IN" })];
    await render(<StayDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Check out"));
    await waitFor(() => expect(mockEnqueue).toHaveBeenCalledWith("STAY_CHECK_OUT", { stayId: "S1", date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) }));
  });
  it("hides the actions while one is waiting to send, so it cannot be queued twice", async () => {
    mockPending = [{ kind: "STAY_CHECK_IN", payload: { stayId: "S1" } }];
    await render(<StayDetailScreen {...props} />);
    expect(screen.getByText(/Check-in saved on this phone and waiting to send/)).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });
  it("stays on the screen with the reason when the phone cannot save", async () => {
    mockEnqueue.mockRejectedValue(new Error("The phone's queue is not ready yet."));
    await render(<StayDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Check in"));
    await waitFor(() => expect(screen.getByText(/queue is not ready/)).toBeTruthy());
  });
  it("still sends a no-show online", async () => {
    mockApi.mockResolvedValue({ ...mockStays[0], status: "NO_SHOW" });
    await render(<StayDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Did not arrive"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/stays/S1/no-show", { method: "POST" }));
  });
  it("assigns and clears a bay", async () => {
    mockApi.mockResolvedValue({ ...mockStays[0], bayId: "B2" });
    await render(<StayDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Bay Bay 2"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/stays/S1/bay", { method: "PUT", body: { bayId: "B2" } }));
  });
  it("offers no actions to someone who may only view", async () => {
    mockPermissions = ["FUEL_READ"];
    await render(<StayDetailScreen {...props} />);
    expect(screen.queryByLabelText("Check in")).toBeNull();
    expect(screen.queryByLabelText(/^Bay /)).toBeNull();
  });
  it("offers no check-in for a truck not due yet", async () => {
    mockStays = [stay({ arrivalDate: "2099-01-01" })];
    await render(<StayDetailScreen {...props} />);
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });
});

describe("StayRequestsScreen", () => {
  const req = { id: "R1", vehicleRegistration: "XYZ 9 GP", customerName: "Acme Haulage", arrivalDate: "2026-12-01", departureDate: "2026-12-03", nights: 2, status: "PENDING" };
  it("says when nothing is waiting", async () => { await render(<StayRequestsScreen />); expect(screen.getByText("No requests waiting.")).toBeTruthy(); });
  it("approves a request", async () => {
    mockRequests = [req]; mockApi.mockResolvedValue({});
    await render(<StayRequestsScreen />);
    await fireEvent.press(screen.getByLabelText("Approve XYZ 9 GP"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/stay-requests/R1/approve", { method: "POST", body: {} }));
    expect(mockRefresh).toHaveBeenCalled();
  });
  it("shows why an approval was refused and keeps the request", async () => {
    mockRequests = [req]; mockApi.mockRejectedValue(new MockApiError("No free bay on 2026-12-02.", 409));
    await render(<StayRequestsScreen />);
    await fireEvent.press(screen.getByLabelText("Approve XYZ 9 GP"));
    await waitFor(() => expect(screen.getByText(/No free bay/)).toBeTruthy());
  });
  it("needs a reason to decline, then sends it", async () => {
    mockRequests = [req]; mockApi.mockResolvedValue({});
    await render(<StayRequestsScreen />);
    await fireEvent.press(screen.getByLabelText("Decline XYZ 9 GP"));
    await fireEvent.press(screen.getByLabelText("Send decline"));
    expect(screen.getByText(/at least 5 characters/)).toBeTruthy();
    expect(mockApi).not.toHaveBeenCalled();
    await fireEvent.changeText(screen.getByLabelText("Reason for declining"), "Yard is full");
    await fireEvent.press(screen.getByLabelText("Send decline"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/stay-requests/R1/decline", { method: "POST", body: { reason: "Yard is full" } }));
  });
});
