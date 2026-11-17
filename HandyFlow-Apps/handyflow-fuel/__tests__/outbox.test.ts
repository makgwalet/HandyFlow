import { ApiError } from "@/api/client";
import { flush, MAX_ATTEMPTS, resetFlushForTests, sortForSending, type OutboxItem, type OutboxStore } from "@/offline/outbox";

const mk = (id: string, kind: OutboxItem["kind"], createdAt: string, o: Partial<OutboxItem> = {}): OutboxItem => ({
  id, ownerId: "u1", kind, payload: {}, recordedAt: createdAt, status: "PENDING", attempts: 0, lastError: null, createdAt, ...o,
});

function memoryStore(items: OutboxItem[]): OutboxStore & { all: OutboxItem[] } {
  const all = [...items];
  return {
    all,
    async add(i) { all.push(i); },
    async pending(o) { return all.filter((i) => i.ownerId === o && i.status === "PENDING"); },
    async failed(o) { return all.filter((i) => i.ownerId === o && i.status === "FAILED"); },
    async update(id, patch) { Object.assign(all.find((i) => i.id === id)!, patch); },
    async remove(id) { all.splice(all.findIndex((i) => i.id === id), 1); },
  };
}

beforeEach(() => resetFlushForTests());

describe("outbox order", () => {
  it("sends the trip start, then the delivery, then photos", () => {
    const sorted = sortForSending([mk("p", "EVIDENCE", "1"), mk("c", "COMPLETE", "2"), mk("d", "DISPATCH", "3")]);
    expect(sorted.map((i) => i.id)).toEqual(["d", "c", "p"]);
  });
  it("keeps creation order within the same step", () => {
    expect(sortForSending([mk("b", "COMPLETE", "2"), mk("a", "COMPLETE", "1")]).map((i) => i.id)).toEqual(["a", "b"]);
  });
});

describe("flush", () => {
  it("removes what was sent", async () => {
    const s = memoryStore([mk("a", "DISPATCH", "1")]);
    const r = await flush(s, async () => undefined, "u1");
    expect(r).toEqual({ sent: 1, failed: 0, stopped: false });
    expect(s.all).toHaveLength(0);
  });
  it("only sends the owner's items", async () => {
    const s = memoryStore([mk("a", "DISPATCH", "1"), mk("b", "DISPATCH", "2", { ownerId: "u2" })]);
    const sent: string[] = [];
    await flush(s, async (i) => { sent.push(i.id); }, "u1");
    expect(sent).toEqual(["a"]);
    expect(s.all.map((i) => i.id)).toEqual(["b"]);
  });
  it.each([[0, "no signal"], [401, "sign-in expired"], [402, "subscription inactive"]])("stops and keeps everything on %s (%s)", async (status) => {
    const s = memoryStore([mk("a", "DISPATCH", "1"), mk("b", "COMPLETE", "2")]);
    const r = await flush(s, async () => { throw new ApiError("x", status); }, "u1");
    expect(r.stopped).toBe(true);
    expect(s.all).toHaveLength(2);
    expect(s.all.every((i) => i.status === "PENDING" && i.attempts === 0)).toBe(true);
  });
  it.each([400, 403, 404, 409])("parks the item as refused on %s and carries on", async (status) => {
    const s = memoryStore([mk("a", "COMPLETE", "1"), mk("b", "EVIDENCE", "2")]);
    const r = await flush(s, async (i) => { if (i.id === "a") throw new ApiError("Delivery already recorded", status); }, "u1");
    expect(r).toEqual({ sent: 1, failed: 1, stopped: false });
    expect(s.all).toHaveLength(1);
    expect(s.all[0]).toMatchObject({ id: "a", status: "FAILED", lastError: "Delivery already recorded" });
  });
  it("retries a server error later and parks it after the limit", async () => {
    const s = memoryStore([mk("a", "COMPLETE", "1", { attempts: MAX_ATTEMPTS - 2 })]);
    await flush(s, async () => { throw new ApiError("boom", 500); }, "u1");
    expect(s.all[0]).toMatchObject({ status: "PENDING", attempts: MAX_ATTEMPTS - 1 });
    await flush(s, async () => { throw new ApiError("boom", 500); }, "u1");
    expect(s.all[0]).toMatchObject({ status: "FAILED", attempts: MAX_ATTEMPTS });
  });
  it("runs one flush at a time", async () => {
    const s = memoryStore([mk("a", "DISPATCH", "1")]);
    let calls = 0;
    const send = async () => { calls += 1; await new Promise((r) => setTimeout(r, 10)); };
    const [x, y] = await Promise.all([flush(s, send, "u1"), flush(s, send, "u1")]);
    expect(x).toBe(y);
    expect(calls).toBe(1);
  });
  it("lets a programming error escape instead of hiding it", async () => {
    const s = memoryStore([mk("a", "DISPATCH", "1")]);
    await expect(flush(s, async () => { throw new TypeError("bug"); }, "u1")).rejects.toThrow("bug");
    expect(s.all).toHaveLength(1);
  });
});
