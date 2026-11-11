import { describe, it, expect } from "vitest"
import { amountBody, canRunImport, checkInView, emailOk, emptyPerson, groupProblem, groupRows, importFileProblem, importSummary, moneyProblem, personBody, transferProblem, waitlistProblem, walkInProblem } from "./guestOps"

const person = (o: Partial<ReturnType<typeof emptyPerson>> = {}) => ({ ...emptyPerson(), fullName: "Thandi", ...o })

describe("field checks", () => {
  it("emails: blank is allowed where optional, malformed is not", () => {
    expect(emailOk("")).toBe(true); expect(emailOk("a@b.co")).toBe(true)
    for (const b of ["a@b", "a b@c.co", "@x.co"]) expect(emailOk(b)).toBe(false)
  })
  it("money: empty, whole or two-decimal amounts only", () => {
    for (const ok of ["", "0", "150", "150.5", "150.50"]) expect(moneyProblem(ok)).toBeNull()
    for (const bad of ["-1", "1.234", "R150", "1,5", "123456789"]) expect(moneyProblem(bad)).not.toBeNull()
    expect(amountBody("")).toBeNull(); expect(amountBody("150.50")).toBe(150.5)
  })
  it("walk-in needs a name and valid optional email; transfer and waitlist also need an email", () => {
    expect(walkInProblem(person(), "")).toBeNull()
    expect(walkInProblem(person({ fullName: " " }), "")).toMatch(/name/)
    expect(walkInProblem(person({ email: "nope" }), "")).toMatch(/valid email/)
    expect(walkInProblem(person(), "abc")).toMatch(/amount/)
    expect(waitlistProblem(person())).toMatch(/email/)
    expect(waitlistProblem(person({ email: "t@x.co" }))).toBeNull()
    expect(transferProblem(person({ email: "t@x.co" }), "x".repeat(501))).toMatch(/500/)
    expect(transferProblem(person({ email: "t@x.co" }), "sick")).toBeNull()
  })
})

describe("group registration", () => {
  it("ignores blank rows and numbers the bad one", () => {
    const rows = [person(), emptyPerson(), person({ fullName: "", email: "x@y.co" })]
    expect(groupRows(rows)).toHaveLength(2)
    expect(groupProblem(rows, "")).toMatch(/^Person 2: .*name/)
  })
  it("needs someone, at most 50, and a sane amount", () => {
    expect(groupProblem([emptyPerson()], "")).toMatch(/at least one/)
    expect(groupProblem(Array.from({ length: 51 }, () => person()), "")).toMatch(/at most 50/)
    expect(groupProblem([person()], "x")).toMatch(/amount/)
    expect(groupProblem([person(), person({ fullName: "Sipho" })], "100")).toBeNull()
  })
  it("sends empty optional fields as null", () => {
    expect(personBody(person({ email: " a@b.co " }))).toEqual({ fullName: "Thandi", email: "a@b.co", phone: null, company: null, dietaryRequirements: null })
  })
})

describe("csv import", () => {
  it("checks the file before upload", () => {
    expect(importFileProblem(null)).toMatch(/Choose/)
    expect(importFileProblem({ name: "g.xlsx", size: 10 })).toMatch(/\.csv/)
    expect(importFileProblem({ name: "g.csv", size: 0 })).toMatch(/empty/)
    expect(importFileProblem({ name: "g.csv", size: 3 * 1024 * 1024 })).toMatch(/2 MB/)
    expect(importFileProblem({ name: "G.CSV", size: 100 })).toBeNull()
  })
  it("describes a preview and a finished import", () => {
    const r = { dryRun: true, totalRows: 10, imported: 7, duplicates: 2, invalid: 1, rejected: 0 }
    expect(importSummary(r)).toBe("10 rows: 7 would be imported, 2 already registered, 1 invalid.")
    expect(importSummary({ ...r, dryRun: false, duplicates: 0, invalid: 0 })).toBe("10 rows: 7 imported.")
  })
  it("only imports after a preview with something to import and the consent box", () => {
    const r = { dryRun: true, totalRows: 3, imported: 3, duplicates: 0, invalid: 0, rejected: 0 }
    expect(canRunImport(null, true)).toBe(false)
    expect(canRunImport(r, false)).toBe(false)
    expect(canRunImport({ ...r, imported: 0 }, true)).toBe(false)
    expect(canRunImport({ ...r, dryRun: false }, true)).toBe(false)
    expect(canRunImport(r, true)).toBe(true)
  })
})

describe("door result", () => {
  it("never shows a welcome for anything but a valid entry", () => {
    for (const r of ["SUCCESS", "WALK_IN"]) expect(checkInView(r).tone).toBe("ok")
    for (const r of ["PAYMENT_PENDING", "ALREADY_CHECKED_IN"]) expect(checkInView(r).tone).toBe("warn")
    for (const r of ["EXPIRED_TICKET", "TRANSFERRED_TICKET", "CANCELLED_TICKET", "NOT_FOUND", "SOMETHING_NEW"]) expect(checkInView(r).tone).toBe("bad")
  })
  it("tells staff what to do for an unpaid hold and a transferred ticket", () => {
    expect(checkInView("PAYMENT_PENDING").hint).toMatch(/payment/i)
    expect(checkInView("TRANSFERRED_TICKET").title).toMatch(/transferred/i)
  })
})
