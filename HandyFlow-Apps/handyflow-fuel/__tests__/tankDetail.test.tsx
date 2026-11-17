import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TankDetailScreen } from "@depot/TankDetailScreen";
import { ReceiveFuelScreen } from "@depot/ReceiveFuelScreen";

const mockApi = jest.fn();
const mockEnqueue = jest.fn(async () => ({}));
const mockNavigate = jest.fn();
const mockGoBack = jest.fn();
let mockPending: any[] = [];
let mockPermissions = ["FUEL_OPERATIONS_MANAGE"];

jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a) }));
jest.mock("@react-navigation/native", () => ({ useNavigation: () => ({ navigate: mockNavigate, goBack: mockGoBack }) }));
jest.mock("@/offline/OutboxProvider", () => ({ useOutbox: () => ({ enqueue: mockEnqueue, pending: mockPending }) }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { firstName: "Sipho", lastName: "D", email: "s@x", permissions: mockPermissions } }) }));
jest.mock("@depot/useTanks", () => ({ useTanks: () => ({ tanks: [{ id: "T1", name: "Tank 1", fuelType: "DIESEL_50", capacityLitres: 30000, currentLitres: 12000, low: false }] }) }));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View> };
});

const props: any = { route: { params: { tankId: "T1" } } };
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { gcTime: 0 } } })}>{ui}</QueryClientProvider>);

beforeEach(() => { mockApi.mockReset(); mockApi.mockResolvedValue({ content: [] }); mockEnqueue.mockClear(); mockNavigate.mockClear(); mockGoBack.mockClear(); mockPending = []; mockPermissions = ["FUEL_OPERATIONS_MANAGE"]; });

describe("TankDetailScreen", () => {
  it("rejects a reading above capacity and queues nothing", async () => {
    await wrap(<TankDetailScreen {...props} />);
    await fireEvent.changeText(screen.getByLabelText("Litres measured (dip)"), "99999");
    await fireEvent.press(screen.getByLabelText("Save dip reading"));
    expect(screen.getByText(/more than the tank/i)).toBeTruthy();
    expect(mockEnqueue).not.toHaveBeenCalled();
  });
  it("saves a reading on the phone, so it works with no signal", async () => {
    await wrap(<TankDetailScreen {...props} />);
    await fireEvent.changeText(screen.getByLabelText("Litres measured (dip)"), "11900");
    await fireEvent.press(screen.getByLabelText("Save dip reading"));
    await waitFor(() => expect(screen.getByText(/saved on this phone/i)).toBeTruthy());
    expect(mockEnqueue).toHaveBeenCalledWith("DIP_READING", { tankId: "T1", body: expect.objectContaining({ actualLitres: 11900, readBy: "Sipho D" }) });
    expect(mockApi.mock.calls.some((c) => c[1]?.method === "POST")).toBe(false);
  });
  it("shows what is waiting to send for this tank only", async () => {
    mockPending = [{ kind: "DIP_READING", payload: { tankId: "T1" } }, { kind: "RECEIVE_FUEL", payload: { tankId: "T1" } }, { kind: "DIP_READING", payload: { tankId: "OTHER" } }];
    await wrap(<TankDetailScreen {...props} />);
    expect(screen.getByText(/2 entries for this tank saved on the phone/)).toBeTruthy();
  });
  it("offers Receive fuel only to those who may", async () => {
    await wrap(<TankDetailScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Receive fuel"));
    expect(mockNavigate).toHaveBeenCalledWith("ReceiveFuel", { tankId: "T1" });
    mockPermissions = ["FUEL_READ"];
    await wrap(<TankDetailScreen {...props} />);
    expect(screen.getByText(/view this tank but not record/)).toBeTruthy();
  });
});

describe("ReceiveFuelScreen", () => {
  const fill = async (litres: string, price: string) => {
    await fireEvent.changeText(screen.getByLabelText("Litres received"), litres);
    await fireEvent.changeText(screen.getByLabelText("Price per litre (rand)"), price);
  };
  it("refuses more than the room left", async () => {
    await wrap(<ReceiveFuelScreen {...props} />);
    await fill("20000", "21.5");
    await fireEvent.press(screen.getByLabelText("Save receipt"));
    expect(screen.getByText(/of room left/)).toBeTruthy();
    expect(mockEnqueue).not.toHaveBeenCalled();
  });
  it("queues the receipt and goes back", async () => {
    await wrap(<ReceiveFuelScreen {...props} />);
    await fill("10000", "21.5");
    await fireEvent.changeText(screen.getByLabelText("Delivery note number (optional)"), "DN7");
    await fireEvent.press(screen.getByLabelText("Save receipt"));
    await waitFor(() => expect(mockGoBack).toHaveBeenCalled());
    expect(mockEnqueue).toHaveBeenCalledWith("RECEIVE_FUEL", { tankId: "T1", body: expect.objectContaining({ litresReceived: 10000, pricePerLitre: 21.5, deliveryNote: "DN7" }) });
  });
  it("does nothing for someone without permission", async () => {
    mockPermissions = ["FUEL_READ"];
    await wrap(<ReceiveFuelScreen {...props} />);
    expect(screen.getByText(/do not have permission/)).toBeTruthy();
    expect(screen.queryByLabelText("Save receipt")).toBeNull();
  });
});
