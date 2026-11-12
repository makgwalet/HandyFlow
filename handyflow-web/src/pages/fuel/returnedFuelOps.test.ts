import { describe, it, expect } from "vitest"
import { validateReturn, loadLabel } from "./returnedFuelOps"

describe("returnedFuelOps", () => {
  it("needs a tank and a positive quantity no bigger than what is still to confirm", () => {
    expect(validateReturn("", "10", 50)).toMatch(/Choose the tank/)
    expect(validateReturn("t", "", 50)).toMatch(/Enter the litres/)
    expect(validateReturn("t", "0", 50)).toMatch(/Enter the litres/)
    expect(validateReturn("t", "abc", 50)).toMatch(/Enter the litres/)
    expect(validateReturn("t", "51", 50)).toMatch(/Only 50 L/)
    expect(validateReturn("t", "50", 50)).toBeNull()
  })
  it("names a load from what is known about it", () => {
    expect(loadLabel({ deliveryId: "d", customerName: "Acme", vehicleReg: "GP 1", driverName: null, loadedLitres: 1, declaredReturned: 1, confirmedBack: 0, outstanding: 1, lastAttemptAt: "x" })).toBe("Acme · GP 1")
    expect(loadLabel({ deliveryId: "d", customerName: null, vehicleReg: null, driverName: null, loadedLitres: null, declaredReturned: 1, confirmedBack: 0, outstanding: 1, lastAttemptAt: "x" })).toBe("Load")
  })
})
