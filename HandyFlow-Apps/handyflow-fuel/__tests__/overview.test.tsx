import React from "react";
import { render, screen } from "@testing-library/react-native";
import { attention, litres, summarySchema, type Summary } from "@overview/summary";
import { OverviewScreen } from "@overview/OverviewScreen";

let mockState: any = {};
jest.mock("@overview/useSummary", () => ({ useSummary: () => mockState }));
jest.mock("react-native-safe-area-context", () => ({ SafeAreaView: ({ children }: any) => children }));
jest.mock("@/ui/SyncBanner", () => ({ SyncBanner: () => null }));
jest.mock("@react-navigation/native", () => ({ useNavigation: () => ({ navigate: jest.fn() }) }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { permissions: ["FUEL_READ"] } }) }));

const sum = (o: Partial<Summary> = {}): Summary => ({ today: "2026-11-18", monthLitres: 123456.4, monthDispatches: 1, monthReceipts: 2, dailyLitres: [], negativeVarianceReadings: 0, openDeliveries: 5, inTransitDeliveries: 2, overdueDeliveries: 0, nextDeliveries: [], ...o });
const base = { summary: null, savedAt: null, isLoading: false, error: null, refetch: jest.fn() };

describe("summary", () => {
  it("reads the server shape, including litres sent as text", () => {
    const r = summarySchema.parse({ today: "2026-11-18", monthLitres: "1500.50", monthDispatches: 3, monthReceipts: 1, dailyLitres: ["10", 20], negativeVarianceReadings: 0, openDeliveries: 1, inTransitDeliveries: 0, overdueDeliveries: 0, nextDeliveries: [{ id: "a", status: "SCHEDULED", fuelType: "DIESEL_50", litresOrdered: "5000", scheduledAt: "2026-11-19T06:00:00Z" }] });
    expect(r.monthLitres).toBe(1500.5);
    expect(r.nextDeliveries[0]?.litresOrdered).toBe(5000);
  });
  it("formats litres and handles missing", () => { expect(litres(1234.6)).toMatch(/^1\D?235 L$/); expect(litres(null)).toBe("0 L"); });
  it("lists attention, worst first, and nothing when all is well", () => {
    expect(attention(sum())).toEqual([]);
    const a = attention(sum({ overdueDeliveries: 2, negativeVarianceReadings: 1 }));
    expect(a.map((x) => x.tone)).toEqual(["danger", "warn"]);
    expect(a[0]?.label).toContain("2 deliveries are overdue");
    expect(a[1]?.label).toContain("1 dip reading is");
  });
});

describe("OverviewScreen", () => {
  it("shows the figures and what is overdue", async () => {
    mockState = { ...base, summary: sum({ overdueDeliveries: 1, nextDeliveries: [{ id: "a", status: "IN_TRANSIT", fuelType: "ULP95", litresOrdered: 8000, scheduledAt: null }] }) };
    await render(<OverviewScreen />);
    expect(screen.getByText(/1 delivery is overdue/)).toBeTruthy();
    expect(screen.getByText("Open deliveries")).toBeTruthy();
    expect(screen.getByText(/2 on the road/)).toBeTruthy();
    expect(screen.getByText(/ULP95 · .*8.*000 L · in transit/)).toBeTruthy();
  });
  it("labels figures shown from the saved copy", async () => {
    mockState = { ...base, summary: sum(), savedAt: "2026-11-18T08:00:00Z" };
    await render(<OverviewScreen />);
    expect(screen.getByText(/No signal\. Showing the figures saved at/)).toBeTruthy();
  });
  it("shows the error when there is nothing saved", async () => {
    mockState = { ...base, error: "Server error" };
    await render(<OverviewScreen />);
    expect(screen.getByText("Server error")).toBeTruthy();
  });
});
