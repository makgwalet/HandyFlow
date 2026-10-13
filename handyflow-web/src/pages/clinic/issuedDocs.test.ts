import { beforeEach, describe, expect, it, vi } from "vitest"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import { canSend, downloadPrescription, errorMessage, sentLine, standingNumber } from "./issuedDocs"

beforeEach(() => {
  get.mockReset(); post.mockReset()
  ;(URL as any).createObjectURL = vi.fn(() => "blob:x"); (URL as any).revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
})

const conflict = (message: string) => ({ response: { status: 409, data: new Blob([JSON.stringify({ message })]) } })
const MSG = "Prescription RX-2026-000007 was issued before this visit changed. Reissue it with a reason; the earlier copy will be withdrawn."

describe("issuedDocs (0203)", () => {
  it("reads the server's message from a JSON or blob body", async () => {
    expect(await errorMessage({ response: { data: { message: "plain" } } }, "x")).toBe("plain")
    expect(await errorMessage({ response: { data: new Blob(['{"message":"in a blob"}']) } }, "x")).toBe("in a blob")
    expect(await errorMessage({ response: { data: new Blob(["%PDF not json"]) } }, "fallback")).toBe("fallback")
  })

  it("finds the standing number in the refusal", () => {
    expect(standingNumber(MSG)).toBe("RX-2026-000007")
    expect(standingNumber("nothing here")).toBeNull()
  })

  it("downloads the prescription without asking anything when it stands unchanged", async () => {
    get.mockResolvedValue({ data: new Blob(["%PDF"]) })
    const ask = vi.fn()
    expect(await downloadPrescription("c1", ask)).toBeNull()
    expect(get).toHaveBeenCalledWith("/api/v1/clinic/consultations/c1/prescription-pdf", { responseType: "blob" })
    expect(ask).not.toHaveBeenCalled()
  })

  it("asks for a reason when it changed, and reissues only with one", async () => {
    get.mockRejectedValue(conflict(MSG))
    post.mockResolvedValue({ data: new Blob(["%PDF"]) })
    const ask = vi.fn().mockResolvedValue("Medicine added after signing")
    expect(await downloadPrescription("c1", ask)).toBeNull()
    expect(ask).toHaveBeenCalledWith("RX-2026-000007", MSG)
    expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/c1/reissued-prescription-pdf", { reason: "Medicine added after signing" }, { responseType: "blob" })
  })

  it("does nothing when the question is cancelled", async () => {
    get.mockRejectedValue(conflict(MSG))
    expect(await downloadPrescription("c1", vi.fn().mockResolvedValue(null))).toBeNull()
    expect(post).not.toHaveBeenCalled()
  })

  it("passes any other failure back as a message", async () => {
    get.mockRejectedValue({ response: { status: 403, data: new Blob(['{"message":"You do not have permission to print or export documents from a restricted record."}']) } })
    expect(await downloadPrescription("c1", vi.fn())).toMatch(/restricted record/)
  })

  it("describes delivery and when a document can be sent", () => {
    expect(sentLine({ lastSentAt: null })).toBe("Not sent")
    expect(sentLine({ lastSentAt: "2026-10-13T10:00:00Z", lastSentTo: "a@b.co" })).toMatch(/^Sent to a@b.co on 13 Oct 2026$/)
    expect(canSend({ status: "ISSUED", copyKept: true })).toBe(true)
    expect(canSend({ status: "VOIDED", copyKept: true })).toBe(false)
    expect(canSend({ status: "ISSUED", copyKept: false })).toBe(false)
  })
})
