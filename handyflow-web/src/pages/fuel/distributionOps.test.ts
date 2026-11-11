import { describe, it, expect } from "vitest"
import { canReplace, canVerify, emptyEvidence, hasVerifiedProof, openCount, toEvidenceRequest, validateEvidence, validateNote, varianceLabel, type Evidence } from "./distributionOps"

const ev = (o: Partial<Evidence> = {}): Evidence => ({ id: "e1", deliveryId: "d1", kind: "PHOTO", fileRef: "f.jpg", capturedAt: "2026-11-11T10:00:00Z", capturedBy: "alice", status: "ACTIVE", locked: false, ...o })

describe("distributionOps", () => {
  it("labels short and over deliveries", () => {
    expect(varianceLabel({ varianceLitres: -100 })).toBe("100 L short")
    expect(varianceLabel({ varianceLitres: 50 })).toBe("50 L over")
    expect(varianceLabel({ varianceLitres: 0 })).toBe("No difference")
  })
  it("counts variances not yet closed", () => {
    expect(openCount(undefined)).toBe(0)
    expect(openCount({ toleranceSet: true, variances: [{ status: "OPEN" }, { status: "INVESTIGATING" }, { status: "CLOSED" }] as any })).toBe(2)
  })
  it("needs a note of 5 characters", () => { expect(validateNote("no")).toMatch(/5/); expect(validateNote(" fine ok ")).toBeNull() })
  it("validates file evidence and GPS evidence", () => {
    expect(validateEvidence(emptyEvidence())).toMatch(/file/)
    expect(validateEvidence({ ...emptyEvidence(), fileRef: "x.jpg" })).toBeNull()
    const g = { ...emptyEvidence(), kind: "GPS" as const }
    expect(validateEvidence(g)).toMatch(/latitude/)
    expect(validateEvidence({ ...g, latitude: "95", longitude: "10" })).toMatch(/-90/)
    expect(validateEvidence({ ...g, latitude: "10", longitude: "190" })).toMatch(/-180/)
    expect(validateEvidence({ ...g, latitude: "-26.2", longitude: "28", accuracyM: "-1" })).toMatch(/Accuracy/)
    expect(validateEvidence({ ...g, latitude: "-26.2", longitude: "28" })).toBeNull()
  })
  it("builds requests with numbers for GPS and nulls where unused", () => {
    expect(toEvidenceRequest({ ...emptyEvidence(), kind: "GPS", latitude: "-26.2", longitude: "28", accuracyM: "12" }))
      .toEqual({ kind: "GPS", fileRef: null, latitude: -26.2, longitude: 28, accuracyM: 12, note: null })
    expect(toEvidenceRequest({ ...emptyEvidence(), fileRef: " a.jpg ", note: " ok " })).toMatchObject({ fileRef: "a.jpg", latitude: null, note: "ok" })
  })
  it("never offers the capturer verification, nor verification or replacement of locked or replaced records", () => {
    expect(canVerify(ev(), "alice")).toBe(false)
    expect(canVerify(ev(), "bob")).toBe(true)
    expect(canVerify(ev({ locked: true }), "bob")).toBe(false)
    expect(canVerify(ev({ status: "SUPERSEDED" }), "bob")).toBe(false)
    expect(canReplace(ev())).toBe(true)
    expect(canReplace(ev({ locked: true }))).toBe(false)
  })
  it("knows whether there is verified proof", () => {
    expect(hasVerifiedProof([ev()])).toBe(false)
    expect(hasVerifiedProof([ev({ locked: true })])).toBe(true)
    expect(hasVerifiedProof([ev({ locked: true, status: "SUPERSEDED" })])).toBe(false)
  })
})
