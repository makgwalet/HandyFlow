import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { LoadDetailScreen } from "@driver/LoadDetailScreen";
import { MyLoadsScreen } from "@driver/MyLoadsScreen";
import type { ArrivalCheck } from "@driver/arrival";

const mockNavigate = jest.fn();
const mockEnqueue = jest.fn(async () => ({}));
const mockOpenURL = jest.fn();
let mockLoads: any[] = [];
let mockSavedAt: string | null = null;
let mockPending: any[] = [];
let mockCheck: ArrivalCheck | null = null;
let mockContact: { name?: string; phone?: string } = {};

jest.mock("@react-navigation/native", () => ({ useNavigation: () => ({ navigate: mockNavigate }) }));
jest.mock("react-native-safe-area-context", () => ({ SafeAreaView: ({ children }: any) => children }));
jest.mock("@/ui/SyncBanner", () => ({ SyncBanner: () => null }));
jest.mock("@/offline/OutboxProvider", () => ({ useOutbox: () => ({ pending: mockPending, enqueue: mockEnqueue }) }));
jest.mock("@/access/useAccess", () => ({ useAccess: () => ({ access: { emergencyContact: mockContact } }) }));
jest.mock("@driver/useLoads", () => ({ useLoads: () => ({ loads: mockLoads, savedAt: mockSavedAt, isLoading: false, error: null, refetch: jest.fn() }) }));
jest.mock("@driver/useArrival", () => ({ useArrival: () => ({ check: mockCheck, busy: false, error: null, run: jest.fn() }) }));
jest.mock("@/files", () => ({ keepFile: jest.fn(async (u: string) => u) }));
jest.mock("expo-image-picker", () => ({}));
jest.mock("expo-location", () => ({}));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return {
    Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>,
    Card: ({ children }: any) => <View>{children}</View>,
    styles: { root: {}, body: {}, title: {}, card: {} },
  };
});
Linking_spy();
function Linking_spy() { jest.spyOn(require("react-native").Linking, "openURL").mockImplementation(mockOpenURL as any); }

const load = (o: any = {}) => ({ id: "L1", status: "SCHEDULED", fuelType: "DIESEL_50", litresOrdered: 10000, failedAttempts: 0, deliveryAddress: { street: "1 Mine Rd", city: "Rustenburg" }, ...o });
const good = (): ArrivalCheck => ({ deliveryId: "L1", allowed: true, findings: [], checkedAt: new Date().toISOString() });

beforeEach(() => { mockNavigate.mockClear(); mockEnqueue.mockClear(); mockOpenURL.mockClear(); mockLoads = []; mockSavedAt = null; mockPending = []; mockCheck = null; mockContact = {}; });

describe("MyLoadsScreen", () => {
  it("says so when there are no loads", async () => {
    await render(<MyLoadsScreen />);
    expect(screen.getByText("No loads assigned to you right now.")).toBeTruthy();
  });
  it("lists loads and opens one", async () => {
    mockLoads = [load()];
    await render(<MyLoadsScreen />);
    expect(screen.getByText("1 Mine Rd, Rustenburg")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText(/DIESEL_50 10000 litres/));
    expect(mockNavigate).toHaveBeenCalledWith("LoadDetail", { loadId: "L1" });
  });
  it("labels a list shown from the phone's saved copy", async () => {
    mockLoads = [load()]; mockSavedAt = "2026-01-01T08:00:00Z";
    await render(<MyLoadsScreen />);
    expect(screen.getByText(/No signal\. Showing the list saved at/)).toBeTruthy();
  });
  it("shows what is waiting to send on a load", async () => {
    mockLoads = [load({ status: "IN_TRANSIT" })];
    mockPending = [{ kind: "COMPLETE", payload: { loadId: "L1" } }];
    await render(<MyLoadsScreen />);
    expect(screen.getByText(/delivery saved, waiting to send/)).toBeTruthy();
  });
  it("offers the emergency call only when the company set a contact, and dials it", async () => {
    mockLoads = [load()];
    await render(<MyLoadsScreen />);
    expect(screen.queryByLabelText(/Emergency/)).toBeNull();
    mockContact = { name: "Control room", phone: "+27 11 555 0100" };
    await render(<MyLoadsScreen />);
    await fireEvent.press(screen.getByLabelText("Emergency: call Control room"));
    expect(mockOpenURL).toHaveBeenCalledWith("tel:+27115550100");
  });
});

describe("LoadDetailScreen", () => {
  const props: any = { route: { params: { loadId: "L1" } }, navigation: { navigate: mockNavigate } };

  it("offers Start trip on a scheduled load and queues it", async () => {
    mockLoads = [load()];
    await render(<LoadDetailScreen {...props} />);
    expect(screen.queryByLabelText("Record delivery")).toBeNull();
    await fireEvent.press(screen.getByLabelText("Start trip"));
    expect(mockEnqueue).toHaveBeenCalledWith("DISPATCH", { loadId: "L1" });
  });
  it("hides Start trip once the start is saved on the phone", async () => {
    mockLoads = [load()]; mockPending = [{ kind: "DISPATCH", payload: { loadId: "L1" } }];
    await render(<LoadDetailScreen {...props} />);
    expect(screen.queryByLabelText("Start trip")).toBeNull();
  });
  it("on the road with no arrival check, asks for the check and does not offer the delivery", async () => {
    mockLoads = [load({ status: "IN_TRANSIT" })];
    await render(<LoadDetailScreen {...props} />);
    expect(screen.getByText("Do the arrival check before you offload.")).toBeTruthy();
    expect(screen.queryByLabelText("Record delivery")).toBeNull();
    await fireEvent.press(screen.getByLabelText("Arrival check"));
    expect(mockNavigate).toHaveBeenCalledWith("ArrivalCheck", { loadId: "L1" });
  });
  it("with a blocked check, shows why and does not offer the delivery", async () => {
    mockLoads = [load({ status: "IN_TRANSIT" })];
    mockCheck = { ...good(), allowed: false, findings: [{ code: "X", severity: "BLOCK", message: "Tanker is not approved at this site." }] };
    await render(<LoadDetailScreen {...props} />);
    expect(screen.getByText(/Tanker is not approved at this site\./)).toBeTruthy();
    expect(screen.queryByLabelText("Record delivery")).toBeNull();
  });
  it("with a fresh, allowed check, offers the delivery and the signature", async () => {
    mockLoads = [load({ status: "IN_TRANSIT" })]; mockCheck = good();
    await render(<LoadDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Record delivery"));
    expect(mockNavigate).toHaveBeenCalledWith("CompleteLoad", { loadId: "L1" });
    await fireEvent.press(screen.getByLabelText("Receiver's signature"));
    expect(mockNavigate).toHaveBeenCalledWith("Signature", { loadId: "L1" });
  });
  it("always offers Could not deliver on the road, even with a blocked check", async () => {
    mockLoads = [load({ status: "IN_TRANSIT" })]; mockCheck = { ...good(), allowed: false, findings: [{ code: "X", severity: "BLOCK", message: "No." }] };
    await render(<LoadDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Could not deliver"));
    expect(mockNavigate).toHaveBeenCalledWith("FailedAttempt", { loadId: "L1" });
  });
  it("says so when the load is no longer on the list", async () => {
    await render(<LoadDetailScreen {...props} />);
    expect(screen.getByText("This load is no longer on your list.")).toBeTruthy();
  });
});
