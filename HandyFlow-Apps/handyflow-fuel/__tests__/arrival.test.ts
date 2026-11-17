import { FRESH_HOURS, arrivalCheckSchema, blocks, isFresh, notes, offloadGate, reviews, type ArrivalCheck } from "@driver/arrival";

const NOW = Date.parse("2026-01-01T12:00:00Z");
const hoursAgo = (h: number) => new Date(NOW - h * 3_600_000).toISOString();
const check = (o: Partial<ArrivalCheck> = {}): ArrivalCheck => ({ deliveryId: "L1", allowed: true, findings: [], checkedAt: hoursAgo(1), ...o });

describe("arrival gate", () => {
  it("needs a check before offloading", () => {
    expect(offloadGate(null, NOW)).toMatchObject({ ok: false, reason: expect.stringMatching(/arrival check/i) });
  });
  it("lets a clean, fresh check through", () => expect(offloadGate(check(), NOW)).toEqual({ ok: true, reason: null }));
  it("stops a blocked load and says why", () => {
    const g = offloadGate(check({ allowed: false, findings: [{ code: "X", severity: "BLOCK", message: "Tanker is not approved at this site." }] }), NOW);
    expect(g.ok).toBe(false);
    expect(g.reason).toContain("Tanker is not approved at this site.");
  });
  it("does not stop a driver for a review point or a note", () => {
    const c = check({ findings: [{ code: "R", severity: "REVIEW", message: "Cleaning is old." }, { code: "I", severity: "INFO", message: "FYI" }] });
    expect(offloadGate(c, NOW).ok).toBe(true);
    expect(reviews(c)).toHaveLength(1);
    expect(notes(c)).toHaveLength(1);
    expect(blocks(c)).toHaveLength(0);
  });
  it(`refuses a check older than ${FRESH_HOURS} hours, so an offline phone cannot slip past a safety rule`, () => {
    expect(offloadGate(check({ checkedAt: hoursAgo(FRESH_HOURS - 1) }), NOW).ok).toBe(true);
    const stale = offloadGate(check({ checkedAt: hoursAgo(FRESH_HOURS + 1) }), NOW);
    expect(stale.ok).toBe(false);
    expect(stale.reason).toMatch(/hours old/);
  });
  it("does not trust a check dated in the future (a wrong phone clock)", () => {
    expect(isFresh(check({ checkedAt: hoursAgo(-5) }), NOW)).toBe(false);
  });
  it("a stale check that was blocked still says blocked, not stale", () => {
    expect(offloadGate(check({ allowed: false, checkedAt: hoursAgo(40), findings: [{ code: "X", severity: "BLOCK", message: "No." }] }), NOW).reason).toMatch(/may not offload/);
  });
  it("accepts the server shape", () => {
    expect(arrivalCheckSchema.safeParse({ deliveryId: "a", allowed: true, findings: [{ code: "C", severity: "INFO", message: "m" }], checkedAt: "2026-01-01T00:00:00Z" }).success).toBe(true);
    expect(arrivalCheckSchema.safeParse({ deliveryId: "a", allowed: true, findings: [{ code: "C", severity: "MAYBE", message: "m" }], checkedAt: "x" }).success).toBe(false);
  });
});
