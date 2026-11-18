import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { TabDetailScreen } from "@truckstop/TabDetailScreen";
import { StayDetailScreen } from "@truckstop/StayDetailScreen";
import { tabSchema } from "@truckstop/tabs";
import { staySchema } from "@truckstop/stays";

const mockApi = jest.fn();
const mockRefresh = jest.fn(async () => undefined);
let mockTab: any = null;
let mockStays: any[] = [];
let mockPermissions = ["FUEL_TRUCKSTOP_MANAGE"];
class MockApiError extends Error { constructor(m: string, readonly status: number) { super(m); } get isNetwork() { return this.status === 0; } }

jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a), get ApiError() { return MockApiError; } }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { permissions: mockPermissions } }) }));
jest.mock("react-native-safe-area-context", () => ({ SafeAreaView: ({ children }: any) => children }));
jest.mock("@/ui/SyncBanner", () => ({ SyncBanner: () => null }));
jest.mock("@truckstop/hooks", () => ({
  useYard: () => ({ stays: mockStays }),
  useBays: () => ({ bays: [] }),
  usePendingRequests: () => ({ requests: [], isLoading: false, error: null, refetch: jest.fn() }),
  useRefreshYard: () => mockRefresh,
  failText: (e: any) => (e?.status === 0 ? "No signal. Nothing was saved. Try again when you have signal." : e.message),
}));
jest.mock("@truckstop/tabHooks", () => {
  const actual = jest.requireActual("@truckstop/tabHooks");
  return {
    ...actual,
    useTab: () => ({ tab: mockTab, isLoading: false, error: null }),
    useOfferings: () => ({ offerings: [{ id: "O1", serviceName: "Shower", price: 35, active: true, serviceMissing: false }] }),
    useRefreshTabs: () => mockRefresh,
  };
});
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View>, styles: { root: {}, body: {}, title: {} } };
});

const line = { id: "L1", description: "Shower", quantity: 1, unitPrice: 35, amount: 35 };
const mk = (o: any = {}) => tabSchema.parse({ id: "T1", vehicleRegistration: "ABC 123 GP", status: "OPEN", lines: [line], total: 35, ...o });
const props = { route: { params: { tabId: "T1" } } } as any;

beforeEach(() => { mockApi.mockReset(); mockRefresh.mockClear(); mockPermissions = ["FUEL_TRUCKSTOP_MANAGE"]; mockTab = mk(); mockStays = []; });

describe("TabDetailScreen", () => {
  it("settles at the till with the chosen method and shows the server's answer", async () => {
    mockApi.mockResolvedValue(mk({ status: "SETTLED", paymentMethod: "CARD" }));
    await render(<TabDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Paid by Card"));
    await waitFor(() => expect(screen.getByText("Settled.")).toBeTruthy());
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs/T1/settle", { method: "POST", body: { paymentMethod: "CARD" } });
  });
  it("charges an account tab to the account, with no till buttons", async () => {
    mockTab = mk({ accountId: "A1" });
    mockApi.mockResolvedValue(mk({ accountId: "A1", status: "SETTLED" }));
    await render(<TabDetailScreen {...props} />);
    expect(screen.queryByLabelText("Paid by Card")).toBeNull();
    await fireEvent.press(screen.getByLabelText("Charge to the customer's account"));
    await waitFor(() => expect(screen.getByText("Charged to the account.")).toBeTruthy());
  });
  it("adds a service by id and quantity; the price is never sent", async () => {
    mockApi.mockResolvedValue(mk());
    await render(<TabDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Shower"));
    await fireEvent.press(screen.getByLabelText("Add to tab"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs/T1/lines", { method: "POST", body: { offeringId: "O1", quantity: 1 } }));
  });
  it("will not void without a reason, then voids with one", async () => {
    mockApi.mockResolvedValue(mk({ status: "VOID", voidReason: "Wrong truck" }));
    await render(<TabDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Void this tab"));
    await fireEvent.press(screen.getByLabelText("Confirm void"));
    expect(screen.getByText(/at least 5/)).toBeTruthy(); expect(mockApi).not.toHaveBeenCalled();
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Wrong truck");
    await fireEvent.press(screen.getByLabelText("Confirm void"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs/T1/void", { method: "POST", body: { reason: "Wrong truck" } }));
  });
  it("offline: says nothing was saved and keeps the tab open", async () => {
    mockApi.mockRejectedValue(new MockApiError("offline", 0));
    await render(<TabDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Paid by Cash"));
    await waitFor(() => expect(screen.getByText(/No signal\. Nothing was saved/)).toBeTruthy());
  });
  it("a revoked permission shows no actions", async () => {
    mockPermissions = ["FUEL_READ"];
    await render(<TabDetailScreen {...props} />);
    expect(screen.queryByLabelText("Paid by Cash")).toBeNull();
    expect(screen.queryByLabelText("Void this tab")).toBeNull();
  });
  it("a settled tab offers nothing", async () => {
    mockTab = mk({ status: "SETTLED", paymentMethod: "CASH" });
    await render(<TabDetailScreen {...props} />);
    expect(screen.queryByLabelText("Paid by Cash")).toBeNull();
    expect(screen.queryByLabelText("Add to tab")).toBeNull();
  });
});

describe("cancelling a stay", () => {
  const stay = (o: any = {}) => staySchema.parse({ id: "S1", vehicleRegistration: "ABC 123 GP", arrivalDate: "2030-01-01", departureDate: "2030-01-03", status: "BOOKED", nights: 2, ...o });
  const sp = { route: { params: { stayId: "S1" } } } as any;
  it("needs a reason, then sends it", async () => {
    mockStays = [stay()];
    mockApi.mockResolvedValue({ ...mockStays[0], status: "CANCELLED" });
    await render(<StayDetailScreen {...sp} />);
    await fireEvent.press(screen.getByLabelText("Cancel this stay"));
    await fireEvent.press(screen.getByLabelText("Confirm cancellation"));
    expect(screen.getByText("Give a reason for the cancellation.")).toBeTruthy(); expect(mockApi).not.toHaveBeenCalled();
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Driver rang");
    await fireEvent.press(screen.getByLabelText("Confirm cancellation"));
    await waitFor(() => expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/stays/S1/cancel", { method: "POST", body: { reason: "Driver rang" } }));
  });
  it("is not offered once the truck is in the yard", async () => {
    mockStays = [stay({ status: "CHECKED_IN" })];
    await render(<StayDetailScreen {...sp} />);
    expect(screen.queryByLabelText("Cancel this stay")).toBeNull();
  });
});
