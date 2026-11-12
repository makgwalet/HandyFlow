import { describe, it, expect } from "vitest"
import { emptyStay, validateStay, validateSettings, nightsText, stayActions, type Stay } from "./stayOps"

const TODAY = "2026-11-12"
const form = (o: any = {}) => ({ ...emptyStay(TODAY), vehicleRegistration: "GP 12 AB", departureDate: "2026-11-14", ...o })
const stay = (o: Partial<Stay> = {}): Stay => ({ id: "s", vehicleRegistration: "GP 1", driverName: null, driverPhone: null, arrivalDate: TODAY, departureDate: "2026-11-14", actualDepartureDate: null,
  status: "BOOKED", ratePerNight: 150, nights: 2, amount: 300, note: null, cancelledReason: null, overstaying: false, checkedInAt: null, checkedOutAt: null, ...o })

describe("stayOps", () => {
  it("needs a rate, a registration, both dates, an arrival not in the past and a departure after it", () => {
    expect(validateStay(form(), TODAY, true)).toBeNull()
    expect(validateStay(form(), TODAY, false)).toMatch(/nightly rate/)
    expect(validateStay(form({ vehicleRegistration: " " }), TODAY, true)).toMatch(/registration/)
    expect(validateStay(form({ departureDate: "" }), TODAY, true)).toMatch(/dates/)
    expect(validateStay(form({ arrivalDate: "2026-11-11" }), TODAY, true)).toMatch(/past/)
    expect(validateStay(form({ departureDate: TODAY }), TODAY, true)).toMatch(/after the arrival/)
  })
  it("settings may be blank, but not a negative rate or zero or fractional bays", () => {
    expect(validateSettings("", "")).toBeNull(); expect(validateSettings("150", "12")).toBeNull()
    expect(validateSettings("-1", "")).toMatch(/zero or more/); expect(validateSettings("abc", "")).toMatch(/zero or more/)
    expect(validateSettings("", "0")).toMatch(/whole number/); expect(validateSettings("", "2.5")).toMatch(/whole number/)
  })
  it("says nights with the right grammar", () => { expect(nightsText(stay({ nights: 1 }))).toBe("1 night"); expect(nightsText(stay())).toBe("2 nights") })
  it("offers the buttons that fit the status and the date", () => {
    expect(stayActions(stay(), TODAY)).toEqual(["checkIn", "extend", "cancel", "noShow"])
    expect(stayActions(stay({ arrivalDate: "2026-11-13" }), TODAY)).toEqual(["extend", "cancel"])   // not due yet
    expect(stayActions(stay({ status: "CHECKED_IN" }), TODAY)).toEqual(["checkOut", "extend"])
    for (const status of ["CHECKED_OUT", "CANCELLED", "NO_SHOW"] as const) expect(stayActions(stay({ status }), TODAY)).toEqual([])
  })
})
