import { alreadyRecorded, canRecordDip, dipBody, fillText, sortTanks, tankSchema, validateDip, varianceText, type Tank } from "@depot/tanks";

const tank = (o: Partial<Tank> = {}): Tank => ({ id: "t", name: "Tank 1", fuelType: "DIESEL_50", capacityLitres: 30000, currentLitres: 12000, low: false, ...o });

describe("tanks", () => {
  it("accepts the server shape and ignores extra fields", () => {
    expect(tankSchema.parse({ id: "a", name: "T", fuelType: "ULP95", capacityLitres: 1, currentLitres: 1, createdAt: "x" }).low).toBe(false);
  });
  it("shows litres and percentage", () => expect(fillText(tank())).toMatch(/12.000 L of 30.000 L \(40%\)/));
  it("lists low tanks first, then by name", () => {
    expect(sortTanks([tank({ id: "b", name: "B" }), tank({ id: "a", name: "A" }), tank({ id: "c", name: "Z", low: true })]).map((t) => t.id)).toEqual(["c", "a", "b"]);
  });
  it("lets only managers record a dip", () => {
    expect(canRecordDip(["FUEL_OPERATIONS_MANAGE"])).toBe(true);
    expect(canRecordDip(["FUEL_MANAGE"])).toBe(true);
    expect(canRecordDip(["FUEL_READ", "FUEL_STOCK_MANAGE"])).toBe(false);
  });
});

describe("dip reading", () => {
  it("needs a number", () => {
    expect(validateDip("")).toMatch(/enter/i);
    expect(validateDip("abc")).toMatch(/number/i);
    expect(validateDip("-5")).toMatch(/zero or more/i);
  });
  it("accepts zero and a comma decimal", () => {
    expect(validateDip("0", 100)).toBeNull();
    expect(validateDip("12,5", 100)).toBeNull();
  });
  it("cannot be above the tank's capacity", () => expect(validateDip("30001", 30000)).toMatch(/capacity/));
  it("builds the request body", () => expect(dipBody("12,5", "2026-01-01T00:00:00.000Z", "Sipho D")).toEqual({ actualLitres: 12.5, readAt: "2026-01-01T00:00:00.000Z", readBy: "Sipho D" }));
  it("describes variance", () => {
    expect(varianceText({ varianceLitres: -120.4, negativeVariance: true })).toBe("Short by 120 L");
    expect(varianceText({ varianceLitres: 50, negativeVariance: false })).toBe("Over by 50 L");
    expect(varianceText({ varianceLitres: 0.2, negativeVariance: false })).toBe("Matches the book stock");
    expect(varianceText({ varianceLitres: null, negativeVariance: false })).toBe("No variance calculated");
  });
  it("recognises a reading that already reached the server", () => {
    const r = [{ id: "1", readAt: "2026-01-01T00:00:00Z", actualLitres: 500, negativeVariance: false }];
    expect(alreadyRecorded(r, "2026-01-01T00:00:00.000Z", 500)).toBe(true);
    expect(alreadyRecorded(r, "2026-01-01T00:00:00.000Z", 501)).toBe(false);
    expect(alreadyRecorded(r, "2026-01-01T00:05:00.000Z", 500)).toBe(false);
  });
});
