import { ApiError } from "@/api/client";
import { flush, MAX_ATTEMPTS, sortForSending, type OutboxItem, type OutboxStore } from "@/offline/outbox";
import { acknowledgeItem, gateArrivalItem, gateExitItem, gatePhotoItem, duressItem, evidenceItem, incidentItem, scanItem, type Context } from "@/offline/payloads";

function memoryStore(): OutboxStore & { all: Map<string, OutboxItem> } {
  const all = new Map<string, OutboxItem>();
  return {
    all,
    async add(i) { all.set(i.id, { ...i }); },
    async pending(g) { return [...all.values()].filter((i) => i.status === "PENDING" && i.guardId === g); },
    async failed(g) { return [...all.values()].filter((i) => i.status === "FAILED" && i.guardId === g); },
    async update(id, patch) { const i = all.get(id); if (i) all.set(id, { ...i, ...patch }); },
    async remove(id) { all.delete(id); },
  };
}

let n = 0;
const ctx = (guardId = "g1", at = "2026-11-12T10:00:00.000Z"): Context => ({
  guardId,
  newId: () => `id-${++n}`,
  now: () => new Date(at),
});

describe("queue items", () => {
  it("carry a clientRef equal to their id, and the time the phone recorded them", () => {
    const item = scanItem({ qrCode: "a:b:c", shiftId: "s1", position: null, deviceId: "d" }, ctx());
    expect(item.payload.clientRef).toBe(item.id);
    expect(item.payload.recordedAt).toBe("2026-11-12T10:00:00.000Z");
    expect(item.guardId).toBe("g1");
  });

  it("never carry a guard id, PIN result or geofence result in the body", () => {
    const item = scanItem({ qrCode: "x", shiftId: null, position: { latitude: 1, longitude: 2, accuracy: 5 }, deviceId: "d" }, ctx());
    for (const k of ["guardId", "pinVerified", "geofenceOk", "faceMatchConfidence"]) expect(item.payload).not.toHaveProperty(k);
    expect(item.payload.latitude).toBe(1);
  });

  it("trim the incident title and send empty details as null", () => {
    const item = incidentItem(
      { siteId: "site", shiftId: null, title: "  Gate forced ", description: "  ", severity: "HIGH", type: "THEFT", position: null },
      ctx(),
    );
    expect(item.payload.title).toBe("Gate forced");
    expect(item.payload.description).toBeNull();
  });
});

describe("sending order", () => {
  it("sends panic first, then incidents, then scans, each oldest first", () => {
    const scan1 = scanItem({ qrCode: "a", shiftId: null, position: null, deviceId: "d" }, ctx("g1", "2026-11-12T10:00:00.000Z"));
    const scan2 = scanItem({ qrCode: "b", shiftId: null, position: null, deviceId: "d" }, ctx("g1", "2026-11-12T10:05:00.000Z"));
    const panic = duressItem({ position: null }, ctx("g1", "2026-11-12T10:09:00.000Z"));
    const incident = incidentItem({ siteId: "s", shiftId: null, title: "abc", description: "", severity: "LOW", type: "OTHER", position: null }, ctx("g1", "2026-11-12T10:07:00.000Z"));
    expect(sortForSending([scan2, incident, scan1, panic]).map((i) => i.kind)).toEqual(["DURESS", "INCIDENT", "SCAN", "SCAN"]);
  });
});

describe("flush", () => {
  it("sends everything and empties the queue", async () => {
    const store = memoryStore();
    await store.add(scanItem({ qrCode: "a", shiftId: null, position: null, deviceId: "d" }, ctx()));
    await store.add(scanItem({ qrCode: "b", shiftId: null, position: null, deviceId: "d" }, ctx()));
    const send = jest.fn().mockResolvedValue(undefined);
    const r = await flush(store, send, "g1");
    expect(r).toEqual({ sent: 2, failed: 0, stopped: false });
    expect(store.all.size).toBe(0);
  });

  it("stops at the first network failure and keeps everything", async () => {
    const store = memoryStore();
    await store.add(scanItem({ qrCode: "a", shiftId: null, position: null, deviceId: "d" }, ctx()));
    await store.add(scanItem({ qrCode: "b", shiftId: null, position: null, deviceId: "d" }, ctx()));
    const send = jest.fn().mockRejectedValue(new ApiError("offline", 0));
    const r = await flush(store, send, "g1");
    expect(r.stopped).toBe(true);
    expect(send).toHaveBeenCalledTimes(1);
    expect(store.all.size).toBe(2);
  });

  it("stops on an expired sign-in without marking anything failed", async () => {
    const store = memoryStore();
    await store.add(scanItem({ qrCode: "a", shiftId: null, position: null, deviceId: "d" }, ctx()));
    const r = await flush(store, jest.fn().mockRejectedValue(new ApiError("no", 401)), "g1");
    expect(r.stopped).toBe(true);
    expect([...store.all.values()][0]?.status).toBe("PENDING");
  });

  it("parks a refused request as failed with the server's words, and carries on", async () => {
    const store = memoryStore();
    const bad = scanItem({ qrCode: "bad", shiftId: null, position: null, deviceId: "d" }, ctx());
    const good = scanItem({ qrCode: "good", shiftId: null, position: null, deviceId: "d" }, ctx("g1", "2026-11-12T10:01:00.000Z"));
    await store.add(bad);
    await store.add(good);
    const send = jest.fn(async (i: OutboxItem) => {
      if (i.id === bad.id) throw new ApiError("QR code signature is invalid", 400);
    });
    const r = await flush(store, send, "g1");
    expect(r).toEqual({ sent: 1, failed: 1, stopped: false });
    expect(store.all.get(bad.id)).toMatchObject({ status: "FAILED", lastError: "QR code signature is invalid" });
    expect(store.all.has(good.id)).toBe(false);
  });

  it("keeps a 409 pending and tries again later, until the attempt limit", async () => {
    const store = memoryStore();
    const item = scanItem({ qrCode: "a", shiftId: null, position: null, deviceId: "d" }, ctx());
    await store.add(item);
    const send = jest.fn().mockRejectedValue(new ApiError("already being processed", 409));
    await flush(store, send, "g1");
    expect(store.all.get(item.id)).toMatchObject({ status: "PENDING", attempts: 1 });
    for (let i = 1; i < MAX_ATTEMPTS; i++) await flush(store, send, "g1");
    expect(store.all.get(item.id)).toMatchObject({ status: "FAILED", attempts: MAX_ATTEMPTS });
  });

  it("only sends the signed-in guard's own items on a shared device", async () => {
    const store = memoryStore();
    const mine = scanItem({ qrCode: "mine", shiftId: null, position: null, deviceId: "d" }, ctx("g1"));
    const theirs = scanItem({ qrCode: "theirs", shiftId: null, position: null, deviceId: "d" }, ctx("g2"));
    await store.add(mine);
    await store.add(theirs);
    const send = jest.fn().mockResolvedValue(undefined);
    await flush(store, send, "g1");
    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0]?.[0].id).toBe(mine.id);
    expect(store.all.has(theirs.id)).toBe(true);
  });

  it("runs one flush at a time", async () => {
    const store = memoryStore();
    await store.add(scanItem({ qrCode: "a", shiftId: null, position: null, deviceId: "d" }, ctx()));
    let release: () => void = () => undefined;
    const send = jest.fn(() => new Promise<void>((res) => { release = res; }));
    const first = flush(store, send, "g1");
    const second = flush(store, send, "g1");
    await new Promise((r) => setTimeout(r, 0));
    release();
    await Promise.all([first, second]);
    expect(send).toHaveBeenCalledTimes(1);
  });
});

describe("incident photos", () => {
  it("name their incident by its id and keep their own id as the photo id", () => {
    const incident = incidentItem(
      { siteId: "site", shiftId: null, title: "Gate forced", description: "", severity: "HIGH", type: "THEFT", position: null },
      ctx(),
    );
    const photo = evidenceItem({ incidentRef: incident.id, localUri: "file:///p.jpg", label: "Gate forced" }, ctx());
    expect(photo.kind).toBe("EVIDENCE");
    expect(photo.payload.incidentRef).toBe(incident.id);
    expect(photo.payload.clientRef).toBe(photo.id);
    expect(photo.id).not.toBe(incident.id);
  });

  it("go out after the panic, the report and the scans, however early they were taken", () => {
    const c = ctx();
    const photo = evidenceItem({ incidentRef: "i", localUri: "file:///p.jpg", label: null }, c);
    const scan = scanItem({ qrCode: "q", shiftId: null, position: null, deviceId: "d" }, ctx("g1", "2026-11-12T11:00:00.000Z"));
    const incident = incidentItem(
      { siteId: "s", shiftId: null, title: "Fire", description: "", severity: "LOW", type: "FIRE", position: null },
      ctx("g1", "2026-11-12T11:30:00.000Z"),
    );
    expect(sortForSending([photo, scan, incident]).map((i) => i.kind)).toEqual(["INCIDENT", "SCAN", "EVIDENCE"]);
  });
});

describe("offline post-order acknowledgement", () => {
  const ack = () => acknowledgeItem({ orderId: "o1", version: 3, deviceHardwareId: "dev" }, ctx());

  it("names the exact version read and carries when the guard read it", () => {
    const item = ack();
    expect(item.kind).toBe("ACK");
    expect(item.payload).toMatchObject({ orderId: "o1", version: 3, deviceHardwareId: "dev", recordedAt: "2026-11-12T10:00:00.000Z" });
    expect(item.payload).not.toHaveProperty("guardId");
  });

  it("goes after panic, incidents and scans but before photos", () => {
    const scan = scanItem({ qrCode: "a", shiftId: null, position: null, deviceId: "d" }, ctx());
    const photo = evidenceItem({ incidentRef: "i", localUri: "file:///p.jpg", label: null }, ctx());
    expect(sortForSending([photo, ack(), scan]).map((i) => i.kind)).toEqual(["SCAN", "ACK", "EVIDENCE"]);
  });

  it("is parked as failed at once when the server says a newer version exists (409), never retried", async () => {
    const store = memoryStore();
    const item = ack();
    await store.add(item);
    const send = jest.fn().mockRejectedValue(new ApiError("A newer version of this order has been published. Read it again before acknowledging.", 409));
    const result = await flush(store, send, "g1");
    expect(send).toHaveBeenCalledTimes(1);
    expect(result.failed).toBe(1);
    expect(store.all.get(item.id)).toMatchObject({ status: "FAILED", attempts: 1 });
  });

  it("is kept and retried on no signal", async () => {
    const store = memoryStore();
    const item = ack();
    await store.add(item);
    const result = await flush(store, jest.fn().mockRejectedValue(new ApiError("offline", 0)), "g1");
    expect(result.stopped).toBe(true);
    expect(store.all.get(item.id)?.status).toBe("PENDING");
  });
});

describe("offline gate log", () => {
  const arrival = () =>
    gateArrivalItem(
      { deviceHardwareId: "dev", accessPointId: "ap1", entryType: "VISITOR", personName: "  Thabo ", idNumber: "", company: " ACME ", hostName: "", purpose: "", vehicleRegistration: "ca 123 gp" },
      { ...ctx(), newId: () => "form-ref" },
    );

  it("an arrival keeps the form's own reference, the time it was logged, and tidy fields", () => {
    const item = arrival();
    expect(item.id).toBe("form-ref");
    expect(item.payload).toMatchObject({
      clientRef: "form-ref", recordedAt: "2026-11-12T10:00:00.000Z", personName: "Thabo", company: "ACME",
      idNumber: null, idScanConfidence: null, vehicleRegistration: "CA 123 GP",
    });
    expect(item.payload).not.toHaveProperty("guardId");
  });

  it("an ID number is recorded as typed in by hand", () => {
    const item = gateArrivalItem(
      { deviceHardwareId: "d", accessPointId: "a", entryType: "VISITOR", personName: "Thabo", idNumber: "8001015009087", company: "", hostName: "", purpose: "", vehicleRegistration: "" },
      ctx(),
    );
    expect(item.payload.idScanConfidence).toBe("MANUAL_ENTRY");
  });

  it("a departure or photo can name an arrival that has not been sent yet", () => {
    const exit = gateExitItem({ entryRef: "form-ref", deviceHardwareId: "dev" }, ctx());
    const photo = gatePhotoItem({ entryRef: "form-ref", docType: "ID_DOCUMENT", localUri: "file:///p.jpg" }, ctx());
    expect(exit.payload).toMatchObject({ entryRef: "form-ref", entryId: null });
    expect(photo.payload).toMatchObject({ entryRef: "form-ref", entryId: null, docType: "ID_DOCUMENT" });
  });

  it("sends the arrival before the departure and the photos that name it", () => {
    const exit = gateExitItem({ entryRef: "form-ref", deviceHardwareId: "dev" }, ctx());
    const photo = gatePhotoItem({ entryRef: "form-ref", docType: "GENERAL_PHOTO", localUri: "file:///p.jpg" }, ctx());
    expect(sortForSending([photo, exit, arrival()]).map((i) => i.kind)).toEqual(["GATE_ARRIVAL", "GATE_EXIT", "GATE_PHOTO"]);
  });

  it("treats a departure the server says is already logged as done, not failed", async () => {
    const store = memoryStore();
    const exit = gateExitItem({ entryId: "e1", deviceHardwareId: "dev" }, ctx());
    await store.add(exit);
    const send = jest.fn().mockRejectedValue(new ApiError("This visitor is already logged as departed.", 409));
    const result = await flush(store, send, "g1");
    expect(result).toMatchObject({ sent: 1, failed: 0 });
    expect(store.all.has(exit.id)).toBe(false);
  });

  it("retries a departure that names an arrival the server has not got yet", async () => {
    const store = memoryStore();
    const exit = gateExitItem({ entryRef: "r", deviceHardwareId: "dev" }, ctx());
    await store.add(exit);
    await flush(store, jest.fn().mockRejectedValue(new ApiError("The arrival has not reached the server yet. Try again shortly.", 409)), "g1");
    expect(store.all.get(exit.id)).toMatchObject({ status: "PENDING", attempts: 1 });
  });

  it("parks an arrival the server refuses for good, such as a gate at another site", async () => {
    const store = memoryStore();
    const item = arrival();
    await store.add(item);
    await flush(store, jest.fn().mockRejectedValue(new ApiError("AccessPoint not found", 404)), "g1");
    expect(store.all.get(item.id)).toMatchObject({ status: "FAILED" });
  });
});
