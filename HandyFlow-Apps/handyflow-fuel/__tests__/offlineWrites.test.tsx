import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { sendItem } from "@/offline/send";
import { sortForSending, type OutboxItem } from "@/offline/outbox";
import { BookStayScreen } from "@truckstop/BookStayScreen";

const mockApi = jest.fn();
const mockEnqueue = jest.fn(async () => ({}));
const mockGoBack = jest.fn();
class MockApiError extends Error { constructor(m: string, readonly status: number) { super(m); } get isNetwork() { return this.status === 0; } }
jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => mockApi(...a), get ApiError() { return MockApiError; } }));
jest.mock("@/offline/OutboxProvider", () => ({ useOutbox: () => ({ enqueue: mockEnqueue, pending: [] }) }));
jest.mock("@/auth/AuthContext", () => ({ useAuth: () => ({ session: { permissions: ["FUEL_TRUCKSTOP_MANAGE"] } }) }));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View> };
});

const item = (kind: OutboxItem["kind"], payload: Record<string, unknown>, o: Partial<OutboxItem> = {}): OutboxItem =>
  ({ id: "11111111-1111-4111-8111-111111111111", ownerId: "u", kind, payload, recordedAt: "2026-11-18T07:00:00.000Z", status: "PENDING", attempts: 0, lastError: null, createdAt: "2026-11-18T07:00:00.000Z", ...o });

beforeEach(() => { mockApi.mockReset(); mockApi.mockResolvedValue({}); mockEnqueue.mockClear(); mockGoBack.mockClear(); });

describe("sending queued forecourt writes", () => {
  it("opens a shift with the item id as the client request id and the captured time", async () => {
    await sendItem(item("SHIFT_OPEN", { body: { attendantName: "T", readings: [] } }));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts", { method: "POST", body: { attendantName: "T", readings: [], clientRequestId: "11111111-1111-4111-8111-111111111111", capturedAt: "2026-11-18T07:00:00.000Z" } });
  });
  it("closes a shift the server knows by its id", async () => {
    await sendItem(item("SHIFT_CLOSE", { shiftId: "S1", body: { cash: 1 } }));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/S1/close", expect.objectContaining({ method: "POST" }));
  });
  it("closes a shift opened offline by the id its open was queued under", async () => {
    await sendItem(item("SHIFT_CLOSE", { openRequestId: "OPEN-1", body: { cash: 1 } }));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/by-request/OPEN-1/close", expect.objectContaining({ method: "POST" }));
  });
  it("sends the same id again on a retry, so the server can recognise it", async () => {
    const it1 = item("SHIFT_OPEN", { body: {} });
    await sendItem(it1); await sendItem(it1);
    const ids = mockApi.mock.calls.map((c) => c[1].body.clientRequestId);
    expect(ids[0]).toBe(ids[1]);
  });
});

describe("sending queued truck stop writes", () => {
  it("books, checks in and checks out with the item id", async () => {
    await sendItem(item("STAY_BOOK", { body: { vehicleRegistration: "ABC 1 GP" } }));
    await sendItem(item("STAY_CHECK_IN", { stayId: "S1" }));
    await sendItem(item("STAY_CHECK_OUT", { stayId: "S1", date: "2026-11-18" }));
    const meta = { clientRequestId: "11111111-1111-4111-8111-111111111111", capturedAt: "2026-11-18T07:00:00.000Z" };
    expect(mockApi).toHaveBeenNthCalledWith(1, "/api/v1/fuel/truck-stop/stays", { method: "POST", body: { vehicleRegistration: "ABC 1 GP", ...meta } });
    expect(mockApi).toHaveBeenNthCalledWith(2, "/api/v1/fuel/truck-stop/stays/S1/check-in", { method: "POST", body: meta });
    expect(mockApi).toHaveBeenNthCalledWith(3, "/api/v1/fuel/truck-stop/stays/S1/check-out", { method: "POST", body: { date: "2026-11-18", ...meta } });
  });
  it("never sends a price or a payment: the server prices the stay and the financial integration takes the money", async () => {
    await sendItem(item("STAY_CHECK_OUT", { stayId: "S1", date: "2026-11-18" }));
    const body = mockApi.mock.calls[0][1].body;
    expect(Object.keys(body).sort()).toEqual(["capturedAt", "clientRequestId", "date"]);
  });
});

describe("queue order", () => {
  it("opens before closing, and books before checking in before checking out", () => {
    const at = (n: number) => `2026-11-18T07:00:0${n}.000Z`;
    const sorted = sortForSending([
      item("STAY_CHECK_OUT", {}, { id: "a", createdAt: at(1) }), item("SHIFT_CLOSE", {}, { id: "b", createdAt: at(2) }),
      item("STAY_CHECK_IN", {}, { id: "c", createdAt: at(3) }), item("STAY_BOOK", {}, { id: "d", createdAt: at(4) }), item("SHIFT_OPEN", {}, { id: "e", createdAt: at(5) }),
    ]).map((i) => i.kind);
    expect(sorted.indexOf("SHIFT_OPEN")).toBeLessThan(sorted.indexOf("SHIFT_CLOSE"));
    expect(sorted.indexOf("STAY_BOOK")).toBeLessThan(sorted.indexOf("STAY_CHECK_IN"));
    expect(sorted.indexOf("STAY_CHECK_IN")).toBeLessThan(sorted.indexOf("STAY_CHECK_OUT"));
  });
});

describe("BookStayScreen offline", () => {
  const props = { navigation: { goBack: mockGoBack } } as any;
  it("queues the booking and says availability is only checked by the server", async () => {
    await render(<BookStayScreen {...props} />);
    expect(screen.getByText(/cannot see which bays are free/)).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Vehicle registration"), "abc 1 gp");
    await fireEvent.changeText(screen.getByLabelText("Departure date (yyyy-mm-dd)"), "2999-01-02");
    await fireEvent.changeText(screen.getByLabelText("Arrival date (yyyy-mm-dd)"), "2999-01-01");
    await fireEvent.press(screen.getByLabelText("Book the stay"));
    await waitFor(() => expect(mockEnqueue).toHaveBeenCalledWith("STAY_BOOK", { body: expect.objectContaining({ vehicleRegistration: "ABC 1 GP", arrivalDate: "2999-01-01" }) }));
    expect(mockApi).not.toHaveBeenCalled();
  });
});
