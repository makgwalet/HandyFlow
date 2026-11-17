import type { Access } from "@/api/access";
import { defaultMode, hasFuelAccess, modesFor } from "@/access/modes";

const access = (o: Partial<Access> = {}): Access => ({ legacyMode: false, activeDivisions: ["DISTRIBUTION", "TRUCK_STOP"], scope: { restricted: false, divisions: [], depotIds: [] }, emergencyContact: {}, ...o });

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

  describe("with the company's divisions and the person's own limits", () => {
    it("hides a mode whose division the company has not switched on", () => {
      expect(modesFor(["FUEL_MANAGE"], access()).map((m) => m.id)).toEqual(["driver", "truckstop", "depot", "manager"]);
    });
    it("opens everything for a company not set up by division", () => {
      expect(modesFor(["FUEL_MANAGE"], access({ legacyMode: true, activeDivisions: [] })).map((m) => m.id)).toHaveLength(5);
    });
    it("limits a person to their own divisions", () => {
      const a = access({ activeDivisions: ["DISTRIBUTION", "TRUCK_STOP", "RETAIL_FORECOURT"], scope: { restricted: true, divisions: ["TRUCK_STOP"], depotIds: [] } });
      expect(modesFor(["FUEL_MANAGE"], a).map((m) => m.id)).toEqual(["truckstop", "manager"]);
    });
    it("does not hide anything while access is not known yet", () => {
      expect(modesFor(["FUEL_MANAGE"], null)).toHaveLength(5);
    });
    it("never adds a mode the permissions do not allow", () => {
      expect(modesFor(["FUEL_DRIVER"], access({ legacyMode: true })).map((m) => m.id)).toEqual(["driver"]);
    });
  });
});
