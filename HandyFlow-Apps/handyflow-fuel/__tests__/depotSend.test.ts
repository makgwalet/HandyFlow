import { sendDepotItem } from "@depot/send";
import { sendItem } from "@/offline/send";
import { sortForSending, type OutboxItem } from "@/offline/outbox";

const mockApi = jest.fn(async () => ({}));
jest.mock("@/api/client", () => ({ api: (...a: unknown[]) => (mockApi as any)(...a), ApiError: class extends Error {} }));
jest.mock("@/files", () => ({ removeFile: jest.fn() }));

const item = (kind: OutboxItem["kind"], payload: Record<string, unknown>, id = "id-1", createdAt = "2026-01-01T00:00:00Z"): OutboxItem =>
  ({ id, ownerId: "u", kind, payload, recordedAt: createdAt, status: "PENDING", attempts: 0, lastError: null, createdAt });

beforeEach(() => mockApi.mockClear());

describe("depot sender", () => {
  it("posts a dip reading with the item id as clientRequestId", async () => {
    await sendDepotItem(item("DIP_READING", { tankId: "T1", body: { actualLitres: 5, readAt: "x", readBy: "S" } }, "abc"));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/tanks/T1/dip-readings", { method: "POST", body: { actualLitres: 5, readAt: "x", readBy: "S", clientRequestId: "abc" } });
  });
  it("posts a receipt with the item id as clientRequestId", async () => {
    await sendDepotItem(item("RECEIVE_FUEL", { tankId: "T1", body: { litresReceived: 100, pricePerLitre: 20, receivedAt: "x" } }, "r1"));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/tanks/T1/receive", { method: "POST", body: expect.objectContaining({ litresReceived: 100, clientRequestId: "r1" }) });
  });
  it("sends the same id again on a retry, which is what makes the replay safe", async () => {
    const i = item("RECEIVE_FUEL", { tankId: "T1", body: { litresReceived: 1, pricePerLitre: 1, receivedAt: "x" } }, "same");
    await sendDepotItem(i); await sendDepotItem(i);
    expect((mockApi.mock.calls as any[]).map((c) => c[1].body.clientRequestId)).toEqual(["same", "same"]);
  });
  it("routes by kind", async () => {
    await sendItem(item("DISPATCH", { loadId: "L1" }));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/driver/loads/L1/dispatch", { method: "POST" });
    await sendItem(item("DIP_READING", { tankId: "T9", body: {} }));
    expect((mockApi.mock.calls as any[])[1][0]).toBe("/api/v1/fuel/tanks/T9/dip-readings");
  });
  it("keeps creation order between a receipt and a dip taken after it", () => {
    const order = sortForSending([item("DIP_READING", {}, "d", "2026-01-01T10:05:00Z"), item("RECEIVE_FUEL", {}, "r", "2026-01-01T10:00:00Z")]);
    expect(order.map((i) => i.id)).toEqual(["r", "d"]);
  });
});
