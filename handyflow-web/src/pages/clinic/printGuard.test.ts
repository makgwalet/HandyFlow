import { afterEach, describe, it, expect, vi } from "vitest"
const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import { REFUSED, allowPrint, printDecision } from "./printGuard"
afterEach(() => { get.mockReset(); post.mockReset() })
const status = (o: object) => get.mockResolvedValue({ data: { data: { restricted: true, canAccess: true, ...o } } })

describe("printDecision", () => {
  it("lets an ordinary record go", () => { expect(printDecision({ restricted: false, canAccess: true })).toBe("FREE"); expect(printDecision(null)).toBe("FREE") })
  it("records the print when a break-glass session is open", () => expect(printDecision({ restricted: true, canAccess: true, breakGlassUntil: "2026-10-13T09:00:00Z" })).toBe("RECORD"))
  it("asks nothing of someone with standing access", () => expect(printDecision({ restricted: true, canAccess: true })).toBe("FREE"))
  it("refuses someone who cannot open the record", () => expect(printDecision({ restricted: true, canAccess: false })).toBe("REFUSE"))
})

describe("allowPrint", () => {
  it("prints an ordinary record without recording anything", async () => {
    get.mockResolvedValue({ data: { data: { restricted: false, canAccess: true } } })
    expect(await allowPrint("p1", "growth chart")).toEqual({ ok: true }); expect(post).not.toHaveBeenCalled()
  })
  it("records the print for the reviewer during break-glass", async () => {
    status({ breakGlassUntil: "2026-10-13T09:00:00Z" }); post.mockResolvedValue({ data: {} })
    expect(await allowPrint("p1", "growth chart")).toEqual({ ok: true })
    expect(post).toHaveBeenCalledWith("/api/v1/clinic/patients/p1/break-glass/print", { document: "growth chart" })
  })
  it("does not print when the print cannot be recorded", async () => {
    status({ breakGlassUntil: "2026-10-13T09:00:00Z" }); post.mockRejectedValue({ response: { status: 403 } })
    expect(await allowPrint("p1", "growth chart")).toEqual({ ok: false, message: REFUSED })
  })
  it("passes on the server's reason", async () => {
    status({ breakGlassUntil: "2026-10-13T09:00:00Z" }); post.mockRejectedValue({ response: { data: { message: "There is no open break-glass session for this record." } } })
    expect((await allowPrint("p1", "x")).message).toBe("There is no open break-glass session for this record.")
  })
  it("does not print a record it could not check", async () => {
    get.mockRejectedValue(new Error("down"))
    const r = await allowPrint("p1", "x"); expect(r.ok).toBe(false); expect(r.message).toMatch(/nothing was printed/)
  })
})
