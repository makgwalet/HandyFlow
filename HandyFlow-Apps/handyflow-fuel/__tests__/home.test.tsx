import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { HomeScreen } from "@/screens/HomeScreen";

const mockNavigate = jest.fn();
let mockPermissions: string[] = [];

jest.mock("@react-navigation/native", () => ({ useNavigation: () => ({ navigate: mockNavigate }) }));
jest.mock("@/access/useAccess", () => ({ useAccess: () => ({ access: null, isLoading: false, error: null }) }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { firstName: "Sipho", lastName: "D", email: "s@x", permissions: mockPermissions }, signOut: jest.fn(), subscriptionInactive: false }) }));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View> };
});

beforeEach(() => mockNavigate.mockClear());

describe("HomeScreen", () => {
  it("opens My loads for a driver", async () => {
    mockPermissions = ["FUEL_DRIVER"];
    await render(<HomeScreen />);
    await fireEvent.press(screen.getByLabelText("My loads"));
    expect(mockNavigate).toHaveBeenCalledWith("DriverLoads");
  });
  it("opens Tanks for a depot user", async () => {
    mockPermissions = ["FUEL_OPERATIONS_MANAGE"];
    await render(<HomeScreen />);
    await fireEvent.press(screen.getByLabelText("Depot"));
    expect(mockNavigate).toHaveBeenCalledWith("DepotTanks");
  });
  it("opens Shifts for forecourt staff", async () => {
    mockPermissions = ["FUEL_FORECOURT"];
    await render(<HomeScreen />);
    await fireEvent.press(screen.getByLabelText("Forecourt"));
    expect(mockNavigate).toHaveBeenCalledWith("ForecourtShifts");
  });
  it("opens the yard for truck stop staff", async () => {
    mockPermissions = ["FUEL_TRUCKSTOP_MANAGE"];
    await render(<HomeScreen />);
    await fireEvent.press(screen.getByLabelText("Truck stop"));
    expect(mockNavigate).toHaveBeenCalledWith("TruckStopYard");
  });
  it("opens Overview for a read-only user", async () => {
    mockPermissions = ["FUEL_READ"];
    await render(<HomeScreen />);
    await fireEvent.press(screen.getByLabelText("Overview"));
    expect(mockNavigate).toHaveBeenCalledWith("Overview");
  });
  it("explains a sign-in with no Fuel access", async () => {
    mockPermissions = ["INVOICE_READ"];
    await render(<HomeScreen />);
    expect(screen.getByText(/no Fuel access/)).toBeTruthy();
  });
});
