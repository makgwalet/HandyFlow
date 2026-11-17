import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TankDetailScreen } from "@depot/TankDetailScreen";

const mockApi = jest.fn();
class MockApiError extends Error { constructor(m: string, readonly status: number) { super(m); } get isNetwork() { return this.status === 0; } }

jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a), get ApiError() { return MockApiError; } }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { firstName: "Sipho", lastName: "D", email: "s@x", permissions: ["FUEL_OPERATIONS_MANAGE"] } }) }));
jest.mock("@depot/useTanks", () => ({ useTanks: () => ({ tanks: [{ id: "T1", name: "Tank 1", fuelType: "DIESEL_50", capacityLitres: 30000, currentLitres: 12000, low: false }] }) }));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View> };
});

const props: any = { route: { params: { tankId: "T1" } } };
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { gcTime: 0 } } })}><TankDetailScreen {...props} /></QueryClientProvider>);

beforeEach(() => mockApi.mockReset());

describe("TankDetailScreen", () => {
  it("rejects a reading above capacity without calling the server for it", async () => {
    mockApi.mockResolvedValue({ content: [] });
    await wrap();
    await fireEvent.changeText(screen.getByLabelText("Litres measured (dip)"), "99999");
    await fireEvent.press(screen.getByLabelText("Save dip reading"));
    expect(screen.getByText(/more than the tank/i)).toBeTruthy();
    expect(mockApi.mock.calls.some((c) => c[1]?.method === "POST")).toBe(false);
  });
  it("saves a reading, and says so", async () => {
    mockApi.mockResolvedValue({ content: [] });
    await wrap();
    await fireEvent.changeText(screen.getByLabelText("Litres measured (dip)"), "11900");
    await fireEvent.press(screen.getByLabelText("Save dip reading"));
    await waitFor(() => expect(screen.getByText("Dip reading saved.")).toBeTruthy());
    const post = mockApi.mock.calls.find((c) => c[1]?.method === "POST")!;
    expect(post[0]).toBe("/api/v1/fuel/tanks/T1/dip-readings");
    expect(post[1].body).toMatchObject({ actualLitres: 11900, readBy: "Sipho D" });
  });
  it("after a lost reply, does not save the same reading twice", async () => {
    let posted: any = null;
    mockApi.mockImplementation(async (path: string, opts?: any) => {
      if (opts?.method === "POST") { posted = opts.body; throw new MockApiError("Cannot reach the server", 0); }
      return { content: posted ? [{ id: "r", readAt: posted.readAt, actualLitres: posted.actualLitres, negativeVariance: false }] : [] };
    });
    await wrap();
    await fireEvent.changeText(screen.getByLabelText("Litres measured (dip)"), "11900");
    await fireEvent.press(screen.getByLabelText("Save dip reading"));
    await waitFor(() => expect(screen.getByText(/No signal/)).toBeTruthy());
    await fireEvent.press(screen.getByLabelText("Save dip reading"));
    await waitFor(() => expect(screen.getByText("That reading was already saved.")).toBeTruthy());
    expect(mockApi.mock.calls.filter((c) => c[1]?.method === "POST")).toHaveLength(1);
  });
});
