import { sendDriverItem } from "@driver/send";
import type { OutboxItem } from "@/offline/outbox";

jest.mock("@/api/client", () => ({ api: jest.fn(async () => ({})), ApiError: class extends Error {} }));
import { api } from "@/api/client";
const mockApi = api as jest.Mock;

const it_ = (kind: OutboxItem["kind"], payload: Record<string, unknown>): OutboxItem =>
  ({ id: "client-1", ownerId: "u", kind, payload, recordedAt: "", status: "PENDING", attempts: 0, lastError: null, createdAt: "" });

beforeEach(() => mockApi.mockClear());

describe("sendDriverItem", () => {
  it("starts a trip", async () => {
    await sendDriverItem(it_("DISPATCH", { loadId: "L9" }));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/driver/loads/L9/dispatch", { method: "POST" });
  });
  it("sends the item id as clientRequestId on a delivery so a retry cannot double-record", async () => {
    await sendDriverItem(it_("COMPLETE", { loadId: "L9", body: { litresDelivered: 100 } }));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/driver/loads/L9/complete", { method: "POST", body: { litresDelivered: 100, clientRequestId: "client-1" } });
  });
  it("sends the item id as clientRequestId on a failed attempt", async () => {
    await sendDriverItem(it_("FAILED_ATTEMPT", { loadId: "L9", body: { reasonCode: "SITE_CLOSED" } }));
    expect(mockApi).toHaveBeenCalledWith("/api/v1/fuel/driver/loads/L9/failed-attempt", { method: "POST", body: { reasonCode: "SITE_CLOSED", clientRequestId: "client-1" } });
  });
  it("uploads a photo as multipart with the same id", async () => {
    await sendDriverItem(it_("EVIDENCE", { loadId: "L9", kind: "PHOTO", uri: "file:///a.jpg", name: "a.jpg", mimeType: "image/jpeg", note: "seal" }));
    const [path, opts] = mockApi.mock.calls[0]!;
    expect(path).toBe("/api/v1/fuel/driver/loads/L9/evidence/file");
    const form = opts.form as FormData;
    expect(form.get("kind")).toBe("PHOTO");
    expect(form.get("clientRequestId")).toBe("client-1");
    expect(form.get("note")).toBe("seal");
  });
});
