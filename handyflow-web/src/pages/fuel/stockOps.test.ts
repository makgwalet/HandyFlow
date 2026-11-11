import { describe, it, expect } from "vitest"
import { availableToPromise, validateAdjust, validateTransfer, validateReserve, movementLabel, mismatches, type TankLine } from "./stockOps"

const tank = (o: Partial<TankLine> = {}): TankLine => ({
  tankId: "t1", tankName: "Diesel 1", fuelType: "DIESEL", depotId: null,
  physical: 5000, reserved: 1000, safetyStock: 500, availableToPromise: 3500, ledgerBalance: 5000, ledgerMatches: true, ...o,
})

describe("stockOps", () => {
  it("available to promise = physical - reserved - safety, never below zero", () => {
    expect(availableToPromise(5000, 1000, 500)).toBe(3500)
    expect(availableToPromise(500, 400, 300)).toBe(0)
  })
  it("adjust: needs a non-zero size, stays within 0..capacity, needs a reason", () => {
    expect(validateAdjust("", "Stocktake", tank())).toMatch(/Enter the litres/)
    expect(validateAdjust("0", "Stocktake", tank())).toMatch(/Enter the litres/)
    expect(validateAdjust("-6000", "Stocktake", tank())).toMatch(/below zero/)
    expect(validateAdjust("6000", "Stocktake", tank(), 10000)).toMatch(/exceed/)
    expect(validateAdjust("-100", "abc", tank())).toMatch(/reason/)
    expect(validateAdjust("-100", "Stocktake loss", tank())).toBeNull()
  })
  it("transfer: two tanks, same fuel, only free litres, reason", () => {
    const a = tank(), b = tank({ tankId: "t2", tankName: "Diesel 2" })
    expect(validateTransfer(undefined, b, "10", "Balance")).toMatch(/both tanks/)
    expect(validateTransfer(a, a, "10", "Balance")).toMatch(/different/)
    expect(validateTransfer(a, tank({ tankId: "t3", fuelType: "PETROL" }), "10", "Balance")).toMatch(/Cannot move DIESEL/)
    expect(validateTransfer(a, b, "0", "Balance")).toMatch(/greater than zero/)
    expect(validateTransfer(a, b, "4001", "Balance")).toMatch(/Only 4/)
    expect(validateTransfer(a, b, "4000", "ab")).toMatch(/reason/)
    expect(validateTransfer(a, b, "4000", "Balance depots")).toBeNull()
  })
  it("reserve: positive and within available to promise", () => {
    expect(validateReserve(undefined, "10")).toMatch(/Choose a tank/)
    expect(validateReserve(tank(), "-1")).toMatch(/greater than zero/)
    expect(validateReserve(tank(), "3500.5")).toMatch(/available to promise/)
    expect(validateReserve(tank(), "3500")).toBeNull()
  })
  it("labels and mismatch detection", () => {
    expect(movementLabel("TRANSFER_IN")).toBe("Transfer in")
    expect(movementLabel("X")).toBe("X")
    expect(mismatches({ tanks: [tank(), tank({ tankId: "t9", ledgerMatches: false })], depots: [] }).map(t => t.tankId)).toEqual(["t9"])
    expect(mismatches(undefined)).toEqual([])
  })
})
