import {
  actionsFor, addressText, completeBody, loadSchema, loadSummary, meterWarning, queuedKinds, sortLoads, stageOf, validateComplete, validateFailure, windowText,
  type CompleteForm, type Load,
} from "@driver/loads";
import type { OutboxItem } from "@/offline/outbox";

const load = (o: Partial<Load> = {}): Load => ({ id: "l1", status: "SCHEDULED", fuelType: "DIESEL_50", litresOrdered: 10000, failedAttempts: 0, ...o });
const item = (kind: OutboxItem["kind"], loadId = "l1"): OutboxItem => ({ id: `${kind}-${loadId}`, ownerId: "u", kind, payload: { loadId }, recordedAt: "", status: "PENDING", attempts: 0, lastError: null, createdAt: "" });
const form = (o: Partial<CompleteForm> = {}): CompleteForm => ({ litres: "9900", receiverName: "Sipho", receiverIdBadge: "", meterStart: "", meterEnd: "", onBehalf: false, onBehalfOf: "", ...o });

describe("load list", () => {
  it("accepts the server shape and ignores extra fields", () => {
    const l = loadSchema.parse({ id: "x", status: "SCHEDULED", fuelType: "ULP95", litresOrdered: 5000, extra: 1 });
    expect(l.failedAttempts).toBe(0);
  });
  it("rejects a load with no id", () => {
    expect(loadSchema.safeParse({ status: "SCHEDULED", fuelType: "X", litresOrdered: 1 }).success).toBe(false);
  });
  it("puts loads on the road first, then route order, finished last", () => {
    const sorted = sortLoads([
      load({ id: "done", status: "DELIVERED" }),
      load({ id: "s2", routeSequence: 2 }),
      load({ id: "s1", routeSequence: 1 }),
      load({ id: "road", status: "IN_TRANSIT", routeSequence: 9 }),
    ]);
    expect(sorted.map((l) => l.id)).toEqual(["road", "s1", "s2", "done"]);
  });
  it("describes windows", () => {
    const f = (s: string) => s.slice(11, 16);
    expect(windowText("2026-01-01T08:00:00Z", "2026-01-01T12:00:00Z", f)).toBe("08:00 to 12:00");
    expect(windowText("2026-01-01T08:00:00Z", null, f)).toBe("from 08:00");
    expect(windowText(null, "2026-01-01T12:00:00Z", f)).toBe("by 12:00");
    expect(windowText(null, null, f)).toBeNull();
  });
  it("builds an address without blanks", () => {
    expect(addressText({ street: "1 Mine Rd", city: "Rustenburg" })).toBe("1 Mine Rd, Rustenburg");
    expect(addressText(null)).toBe("No address");
  });
});

describe("actions respect the phone's own queue", () => {
  it("offers Start on a scheduled load", () => {
    expect(actionsFor(load(), new Set())).toMatchObject({ start: true, complete: false, failed: false });
  });
  it("after Start is saved offline, hides Start and offers delivery", () => {
    const q = queuedKinds([item("DISPATCH")], "l1");
    expect(actionsFor(load(), q)).toMatchObject({ start: false, complete: true, failed: true });
  });
  it("on the road offers delivery and failure", () => {
    expect(actionsFor(load({ status: "IN_TRANSIT" }), new Set())).toMatchObject({ start: false, complete: true, failed: true });
  });
  it("a delivery saved offline cannot be closed twice", () => {
    const q = queuedKinds([item("COMPLETE")], "l1");
    expect(actionsFor(load({ status: "IN_TRANSIT" }), q)).toMatchObject({ complete: false, failed: false });
  });
  it("a failed attempt saved offline also blocks a second close-out", () => {
    const q = queuedKinds([item("FAILED_ATTEMPT")], "l1");
    expect(actionsFor(load({ status: "IN_TRANSIT" }), q).complete).toBe(false);
  });
  it("a delivered load offers nothing to close", () => {
    expect(actionsFor(load({ status: "DELIVERED" }), new Set())).toMatchObject({ start: false, complete: false, failed: false });
  });
  it("only counts the queue for its own load", () => {
    expect(queuedKinds([item("COMPLETE", "other")], "l1").size).toBe(0);
  });
  it("says what is waiting to send", () => {
    expect(loadSummary(load({ status: "IN_TRANSIT" }), new Set(["COMPLETE" as const]))).toContain("delivery saved, waiting to send");
    expect(stageOf(load({ status: "IN_TRANSIT" }))).toBe("ON_THE_ROAD");
  });
});

describe("delivery form", () => {
  it("accepts a normal delivery", () => expect(validateComplete(form())).toBeNull());
  it("needs litres above zero", () => {
    expect(validateComplete(form({ litres: "" }))).toMatch(/litres/i);
    expect(validateComplete(form({ litres: "0" }))).toMatch(/litres/i);
    expect(validateComplete(form({ litres: "abc" }))).toMatch(/litres/i);
  });
  it("accepts a comma as the decimal mark", () => expect(validateComplete(form({ litres: "9900,5" }))).toBeNull());
  it("cannot deliver more than was loaded", () => expect(validateComplete(form({ litres: "10001" }), 10000)).toMatch(/more than the 10000 L loaded/));
  it("needs a receiver", () => expect(validateComplete(form({ receiverName: "  " }))).toMatch(/received/i));
  it("needs the represented person when signed on behalf", () => {
    expect(validateComplete(form({ onBehalf: true }))).toMatch(/on behalf/i);
    expect(validateComplete(form({ onBehalf: true, onBehalfOf: "Thabo" }))).toBeNull();
  });
  it("needs both meter readings or neither, and end above start", () => {
    expect(validateComplete(form({ meterStart: "100" }))).toMatch(/both/i);
    expect(validateComplete(form({ meterStart: "100", meterEnd: "100" }))).toMatch(/higher/i);
    expect(validateComplete(form({ meterStart: "100", meterEnd: "10000" }))).toBeNull();
  });
  it("warns, but does not block, when the meter and litres disagree", () => {
    const f = form({ meterStart: "0", meterEnd: "9000" });
    expect(meterWarning(f)).toMatch(/meter shows 9000 L/);
    expect(validateComplete(f)).toBeNull();
    expect(meterWarning(form({ meterStart: "0", meterEnd: "9900" }))).toBeNull();
  });
  it("builds the request body the server expects", () => {
    expect(completeBody(form({ litres: "9900", receiverIdBadge: " B12 ", meterStart: "1", meterEnd: "9901" }))).toEqual({
      litresDelivered: 9900, receiverName: "Sipho", receiverIdBadge: "B12", meterReadingStart: 1, meterReadingEnd: 9901, signedOnBehalf: false, onBehalfOf: null,
    });
  });
});

describe("failed delivery form", () => {
  it("needs a reason", () => expect(validateFailure({ reason: "", note: "", litresReturned: "" })).toMatch(/reason/i));
  it("needs a description for Other", () => {
    expect(validateFailure({ reason: "OTHER", note: "", litresReturned: "" })).toMatch(/describe/i);
    expect(validateFailure({ reason: "OTHER", note: "Flooded road", litresReturned: "" })).toBeNull();
  });
  it("checks litres coming back against the loading", () => {
    expect(validateFailure({ reason: "SITE_CLOSED", note: "", litresReturned: "5" }, null)).toMatch(/no loading/i);
    expect(validateFailure({ reason: "SITE_CLOSED", note: "", litresReturned: "20000" }, 10000)).toMatch(/more than/i);
    expect(validateFailure({ reason: "SITE_CLOSED", note: "", litresReturned: "-1" }, 10000)).toMatch(/zero or more/i);
    expect(validateFailure({ reason: "SITE_CLOSED", note: "", litresReturned: "0" }, null)).toBeNull();
  });
});
