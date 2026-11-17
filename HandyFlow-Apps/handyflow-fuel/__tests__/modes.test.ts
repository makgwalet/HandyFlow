import { defaultMode, hasFuelAccess, modesFor } from "@/access/modes";

describe("modes", () => {
  it("gives a driver only My loads", () => {
    const m = modesFor(["FUEL_DRIVER"]);
    expect(m.map((x) => x.id)).toEqual(["driver"]);
    expect(defaultMode(m)).toBe("driver");
  });
  it("gives a manager every mode and no default", () => {
    const m = modesFor(["FUEL_MANAGE"]);
    expect(m.map((x) => x.id)).toEqual(["driver", "forecourt", "truckstop", "depot", "manager"]);
    expect(defaultMode(m)).toBeNull();
  });
  it("marks Driver and Depot as built", () => {
    expect(modesFor(["FUEL_MANAGE"]).filter((m) => m.available).map((m) => m.id)).toEqual(["driver", "depot"]);
  });
  it("shows a read-only user the overview only", () => {
    expect(modesFor(["FUEL_READ"]).map((x) => x.id)).toEqual(["manager"]);
  });
  it("shows nothing without a Fuel permission", () => {
    expect(modesFor(["INVOICE_READ"])).toEqual([]);
    expect(hasFuelAccess(["INVOICE_READ"])).toBe(false);
    expect(hasFuelAccess(["FUEL_READ"])).toBe(true);
  });
});
