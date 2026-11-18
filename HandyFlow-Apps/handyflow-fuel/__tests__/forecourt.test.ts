import { ADJUSTMENT_KINDS, adjustmentBody, canRunShifts, closeBody, closeRows, emptyTakings, expectedPreview, openBody, openRows, shiftSchema, sortShifts, validateAdjustment, validateClose, validateOpen, varianceLabel, type Pump, type Shift } from "@forecourt/shifts";

const pump = (o: Partial<Pump> = {}): Pump => ({ id: "P1", name: "Pump 1", fuelType: "DIESEL_50", lastMeter: 1000.5, active: true, onShift: false, ...o });
const shift = (o: Partial<Shift> = {}): Shift => shiftSchema.parse({ id: "S1", attendantName: "Thandi", status: "OPEN", openedAt: "2026-11-18T06:00:00Z", readings: [{ pumpId: "P1", pumpName: "Pump 1", openingMeter: 1000 }, { pumpId: "P2", pumpName: "Pump 2", openingMeter: 500 }], ...o });

describe("open shift", () => {
  it("starts from the pumps that are active and free, with the last meter filled in", () => {
    const rows = openRows([pump(), pump({ id: "P2", onShift: true }), pump({ id: "P3", active: false }), pump({ id: "P4", lastMeter: null })]);
    expect(rows).toEqual([{ pumpId: "P1", meter: "1000.5" }, { pumpId: "P4", meter: "" }]);
  });
  it("needs a name and every meter", () => {
    expect(validateOpen("", [{ pumpId: "P1", meter: "1" }])).toMatch(/name/);
    expect(validateOpen("T", [])).toMatch(/No pumps/);
    expect(validateOpen("T", [{ pumpId: "P1", meter: "" }])).toMatch(/opening meter/);
    expect(validateOpen("T", [{ pumpId: "P1", meter: "-1" }])).toMatch(/opening meter/);
    expect(validateOpen("T", [{ pumpId: "P1", meter: "0" }])).toBeNull();
  });
  it("keeps the price the phone showed for each pump, and sends it so the server can refuse an open at a changed price", () => {
    const rows = openRows([pump({ pricePerLitre: 22.5 })]);
    expect(rows).toEqual([{ pumpId: "P1", meter: "1000.5", price: 22.5 }]);
    expect(openBody("T", rows, "").readings).toEqual([{ pumpId: "P1", openingMeter: 1000.5, expectedPrice: 22.5 }]);
  });
  it("sends no price when the phone has none (or zero), so the check is skipped rather than failing", () => {
    expect(openBody("T", [{ pumpId: "P1", meter: "1" }], "").readings).toEqual([{ pumpId: "P1", openingMeter: 1 }]);
    expect(openBody("T", [{ pumpId: "P1", meter: "1", price: 0 }], "").readings).toEqual([{ pumpId: "P1", openingMeter: 1 }]);
  });
  it("builds the request", () => expect(openBody(" Thandi ", [{ pumpId: "P1", meter: "10,5" }], " ")).toEqual({ attendantName: "Thandi", readings: [{ pumpId: "P1", openingMeter: 10.5 }] }));
});

describe("close shift", () => {
  const rows = (c1: string, c2: string) => [{ pumpId: "P1", closing: c1, test: "" }, { pumpId: "P2", closing: c2, test: "" }];
  it("starts a blank row per pump on the shift", () => expect(closeRows(shift())).toHaveLength(2));
  it("needs every closing meter and refuses one below the opening meter", () => {
    expect(validateClose(shift(), rows("1100", ""), emptyTakings)).toMatch(/Pump 2/);
    expect(validateClose(shift(), rows("999", "600"), emptyTakings)).toMatch(/below its opening meter of 1000/);
    expect(validateClose(shift(), rows("1100", "600"), emptyTakings)).toBeNull();
  });
  it("checks takings and test litres only when typed", () => {
    expect(validateClose(shift(), rows("1100", "600"), { ...emptyTakings, cash: "abc" })).toMatch(/cash/);
    expect(validateClose(shift(), [{ pumpId: "P1", closing: "1100", test: "x" }, { pumpId: "P2", closing: "600", test: "" }], emptyTakings)).toMatch(/Test litres/);
  });
  it("sends empty takings as zero and only the reasons and tests that were typed", () => {
    expect(closeBody([{ pumpId: "P1", closing: "1100,5", test: "5" }], { ...emptyTakings, cash: "1500", card: "300,50" }, " ")).toEqual({
      cash: 1500, card: 300.5, account: 0, other: 0, fleetCard: 0, readings: [{ pumpId: "P1", closingMeter: 1100.5, testLitres: 5 }],
    });
  });
  it("previews litres and value from meters less test litres", () => {
    const p = expectedPreview(shift(), [{ pumpId: "P1", closing: "1010", test: "5" }, { pumpId: "P2", closing: "", test: "" }], { P1: 20, P2: 20 });
    expect(p).toEqual({ litres: 5, value: 100 });
  });
});

describe("adjustments", () => {
  it("offers refund, void and discount, the three the server accepts", () => expect([...ADJUSTMENT_KINDS]).toEqual(["REFUND", "VOID", "DISCOUNT"]));
  it("checks amount and reason like the server does", () => {
    expect(validateAdjustment("", "long enough")).toMatch(/amount/);
    expect(validateAdjustment("0", "long enough")).toMatch(/amount/);
    expect(validateAdjustment("50", "no")).toMatch(/5 characters/);
    expect(validateAdjustment("50", "x".repeat(301))).toMatch(/too long/);
    expect(validateAdjustment("50", "Wrong pump")).toBeNull();
    expect(adjustmentBody("VOID", "12,5", " Wrong pump ")).toEqual({ kind: "VOID", amount: 12.5, reason: "Wrong pump" });
  });
});

describe("shift display", () => {
  it("puts open shifts first, then newest", () => {
    const l = sortShifts([shift({ id: "a", status: "CLOSED", openedAt: "2026-11-18T06:00:00Z" }), shift({ id: "b", status: "CLOSED", openedAt: "2026-11-17T06:00:00Z" }), shift({ id: "c", status: "OPEN", openedAt: "2026-11-10T06:00:00Z" })]);
    expect(l.map((x) => x.id)).toEqual(["c", "a", "b"]);
  });
  it("reads variance against the tolerance: negative is short", () => {
    expect(varianceLabel({ variance: -20, tolerance: 5 })).toEqual({ text: "Short by R 20,00", bad: true });
    expect(varianceLabel({ variance: 3, tolerance: 5 })).toEqual({ text: "Balanced", bad: false });
    expect(varianceLabel({ variance: 12, tolerance: 5 }).text).toMatch(/Over by/);
  });
  it("reads amounts sent as text and tolerates missing ones", () => {
    const s = shiftSchema.parse({ id: "x", attendantName: "A", status: "CLOSED", openedAt: "t", cash: "100.50", variance: "-2.5" });
    expect(s.cash).toBe(100.5); expect(s.card ?? null).toBeNull(); expect(s.readings).toEqual([]);
  });
  it("lets forecourt staff and managers run shifts, not read-only staff", () => {
    expect(canRunShifts(["FUEL_FORECOURT"])).toBe(true);
    expect(canRunShifts(["FUEL_READ"])).toBe(false);
  });
});
