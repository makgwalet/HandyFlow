import { describe, it, expect } from "vitest"
import { emptyStay, validateStay, validateSettings, nightsText, stayActions, nightState, firstFullNight, chargeText, invoiceText, canRaiseInvoice, chargeableAccounts, type Stay, type Night, type Capacity } from "./stayOps"

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

describe("yard capacity helpers", () => {
  const night = (o: Partial<Night> = {}): Night => ({ date: "2026-11-12", used: 1, arrivals: 0, departures: 0, free: 4, ...o })
  it("says open when no bay limit is set", () => { expect(nightState(night({ free: null }), null)).toBe("open") })
  it("says full when no bay is free, tight when one or two are, ok otherwise", () => {
    expect(nightState(night({ used: 5, free: 0 }), 5)).toBe("full")
    expect(nightState(night({ used: 4, free: 1 }), 5)).toBe("tight")
    expect(nightState(night({ used: 3, free: 2 }), 5)).toBe("tight")
    expect(nightState(night({ used: 2, free: 3 }), 5)).toBe("ok")
  })
  it("finds the first full night", () => {
    const c: Capacity = { from: "2026-11-12", bays: 2, overstays: [], nights: [night({ date: "2026-11-12", free: 1 }), night({ date: "2026-11-13", used: 2, free: 0 }), night({ date: "2026-11-14", used: 2, free: 0 })] }
    expect(firstFullNight(c)).toBe("2026-11-13")
    expect(firstFullNight({ ...c, nights: [night()] })).toBeNull()
  })
})

describe("charging a stay to an account", () => {
  const accts = [{ id: "a1", customerName: "Acme", creditHold: false }, { id: "a2", customerName: "Held", creditHold: true }]
  it("names the account, or says paid at the gate", () => {
    expect(chargeText(stay({ accountId: "a1" }), accts)).toBe("Acme")
    expect(chargeText(stay(), accts)).toBe("Paid at the gate")
    expect(chargeText(stay({ accountId: "zz" }), accts)).toBe("Customer account")
  })
  it("says nothing about an invoice for a stay with no account", () => { expect(invoiceText(stay())).toBeNull() })
  it("describes each invoice state", () => {
    expect(invoiceText(stay({ accountId: "a1" }))).toBe("Invoiced at check-out")
    expect(invoiceText(stay({ accountId: "a1", status: "CHECKED_OUT", invoiceState: "INVOICED", invoiceNumber: "INV-7" }))).toBe("Draft invoice INV-7")
    expect(invoiceText(stay({ accountId: "a1", status: "CHECKED_OUT", invoiceState: "FAILED", invoiceError: "boom" }))).toBe("Invoice failed: boom")
    expect(invoiceText(stay({ accountId: "a1", status: "CHECKED_OUT", invoiceState: "LINK_FAILED", invoiceNumber: "INV-8" }))).toMatch(/INV-8 was created but not linked/)
  })
  it("lets only a left stay with a pending or failed invoice be raised again", () => {
    expect(canRaiseInvoice(stay({ accountId: "a1", status: "CHECKED_OUT", invoiceState: "FAILED" }))).toBe(true)
    expect(canRaiseInvoice(stay({ accountId: "a1", status: "CHECKED_OUT", invoiceState: "PENDING" }))).toBe(true)
    expect(canRaiseInvoice(stay({ accountId: "a1", status: "CHECKED_OUT", invoiceState: "INVOICED" }))).toBe(false)
    expect(canRaiseInvoice(stay({ accountId: "a1", status: "CHECKED_OUT", invoiceState: "LINK_FAILED" }))).toBe(false)
    expect(canRaiseInvoice(stay({ accountId: "a1", status: "CHECKED_IN" }))).toBe(false)
    expect(canRaiseInvoice(stay({ status: "CHECKED_OUT" }))).toBe(false)
  })
  it("leaves accounts on credit hold out of the choices", () => { expect(chargeableAccounts(accts).map(a => a.id)).toEqual(["a1"]) })
})
