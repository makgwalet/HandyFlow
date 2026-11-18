import { ACTION_LABEL, actionsFor, activeBays, canRunTruckStop, dayText, groupOf, grouped, staySchema, todayZA, validateDecline, type Stay } from "@truckstop/stays";

const stay = (o: Partial<Stay> = {}): Stay => staySchema.parse({ id: "a", vehicleRegistration: "ABC 123 GP", arrivalDate: "2026-11-18", departureDate: "2026-11-20", status: "BOOKED", nights: 2, ...o });
const TODAY = "2026-11-18";

describe("stay groups", () => {
  it("sorts a stay by where it is today", () => {
    expect(groupOf(stay({ status: "CHECKED_IN" }), TODAY)).toBe("inYard");
    expect(groupOf(stay({ status: "CHECKED_IN", departureDate: "2026-11-17" }), TODAY)).toBe("overstaying");
    expect(groupOf(stay({ status: "CHECKED_IN", overstaying: true }), TODAY)).toBe("overstaying");
    expect(groupOf(stay({ status: "BOOKED", arrivalDate: "2026-11-18" }), TODAY)).toBe("arrivingToday");
    expect(groupOf(stay({ status: "BOOKED", arrivalDate: "2026-11-16" }), TODAY)).toBe("arrivingToday");
    expect(groupOf(stay({ status: "BOOKED", arrivalDate: "2026-11-19" }), TODAY)).toBe("upcoming");
    for (const st of ["CHECKED_OUT", "CANCELLED", "NO_SHOW"]) expect(groupOf(stay({ status: st }), TODAY)).toBe("past");
  });
  it("lists overstaying first and leaves out empty groups", () => {
    const g = grouped([stay({ id: "p", status: "CHECKED_OUT" }), stay({ id: "o", status: "CHECKED_IN", overstaying: true }), stay({ id: "y", status: "CHECKED_IN" })], TODAY);
    expect(g.map((x) => x.group)).toEqual(["overstaying", "inYard", "past"]);
  });
  it("sorts recent stays newest first and the rest by arrival", () => {
    const past = grouped([stay({ id: "1", status: "CHECKED_OUT", arrivalDate: "2026-11-01" }), stay({ id: "2", status: "CHECKED_OUT", arrivalDate: "2026-11-10" })], TODAY)[0]!;
    expect(past.stays.map((s) => s.id)).toEqual(["2", "1"]);
    const up = grouped([stay({ id: "1", arrivalDate: "2026-11-25" }), stay({ id: "2", arrivalDate: "2026-11-20" })], TODAY)[0]!;
    expect(up.stays.map((s) => s.id)).toEqual(["2", "1"]);
  });
});

describe("what can be done", () => {
  it("checks in or marks no-show a truck that is due, nothing for one not due yet", () => {
    expect(actionsFor(stay(), TODAY)).toEqual(["checkIn", "noShow"]);
    expect(actionsFor(stay({ arrivalDate: "2026-11-19" }), TODAY)).toEqual([]);
  });
  it("checks out a truck in the yard and nothing once finished", () => {
    expect(actionsFor(stay({ status: "CHECKED_IN" }), TODAY)).toEqual(["checkOut"]);
    for (const st of ["CHECKED_OUT", "CANCELLED", "NO_SHOW"]) expect(actionsFor(stay({ status: st }), TODAY)).toEqual([]);
  });
  it("has plain labels", () => expect(ACTION_LABEL.noShow).toBe("Did not arrive"));
  it("lets truck stop staff and managers act", () => {
    expect(canRunTruckStop(["FUEL_TRUCKSTOP_MANAGE"])).toBe(true);
    expect(canRunTruckStop(["FUEL_READ"])).toBe(false);
  });
});

describe("helpers", () => {
  it("works out today in South African time, which is ahead of UTC", () => {
    expect(todayZA(new Date("2026-11-17T23:30:00Z"))).toBe("2026-11-18");
    expect(todayZA(new Date("2026-11-18T21:59:00Z"))).toBe("2026-11-18");
  });
  it("shows a day without shifting it by time zone", () => expect(dayText("2026-11-18")).toMatch(/18/));
  it("needs a decline reason of 5 to 300 characters", () => {
    expect(validateDecline("no")).toMatch(/5 characters/);
    expect(validateDecline("x".repeat(301))).toMatch(/too long/);
    expect(validateDecline("Yard is full")).toBeNull();
  });
  it("offers active bays in natural order", () => {
    expect(activeBays([{ id: "1", name: "Bay 10", active: true }, { id: "2", name: "Bay 2", active: true }, { id: "3", name: "Bay 3", active: false }]).map((b) => b.name)).toEqual(["Bay 2", "Bay 10"]);
  });
});
