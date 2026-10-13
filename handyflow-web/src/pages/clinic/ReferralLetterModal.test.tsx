import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"

const post = vi.fn()
const get = vi.fn()
const perms: Record<string, boolean> = {}
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a), get: (...a: any[]) => get(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
import ReferralLetterModal from "./ReferralLetterModal"

const DOC = { id: "d1", number: "RF-2026-000004", type: "REFERRAL", typeLabel: "Referral letter", issuedAt: "2026-10-13T10:00:00Z", status: "ISSUED", copyKept: true }

beforeEach(() => {
  post.mockReset(); get.mockReset(); for (const k of Object.keys(perms)) delete perms[k]
  get.mockImplementation((url: string) => url.endsWith("/issued-documents")
    ? Promise.resolve({ data: { data: { patientId: "p1", documents: [DOC] } } })
    : Promise.resolve({ data: { data: [] } }))
  ;(URL as any).createObjectURL = vi.fn(() => "blob:x"); (URL as any).revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
})
afterEach(cleanup)

const issue = async () => {
  post.mockResolvedValueOnce({ data: new Blob(["x"]) })
  fireEvent.change(screen.getByLabelText(/Reason for referral/), { target: { value: "Chest pain work-up" } })
  fireEvent.change(screen.getByLabelText("Urgency"), { target: { value: "URGENT" } })
  fireEvent.click(screen.getByRole("button", { name: "Generate PDF" }))
  await screen.findByText("RF-2026-000004")
}

describe("ReferralLetterModal", () => {
  it("needs a reason before it will generate", () => {
    render(<ReferralLetterModal consultationId="c1" onClose={() => {}} />)
    expect((screen.getByRole("button", { name: "Generate PDF" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("issues the letter for the consultation, shows its number and sends nothing (0203)", async () => {
    render(<ReferralLetterModal consultationId="c1" onClose={() => {}} />)
    await issue()
    expect(post.mock.calls[0][0]).toBe("/api/v1/clinic/consultations/c1/referral-letter")
    expect(post.mock.calls[0][2].params).toMatchObject({ reason: "Chest pain work-up", urgency: "URGENT" })
    expect(screen.getByText(/Nothing has been sent to the patient/)).toBeTruthy()
    expect(post).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole("button", { name: "Email a copy to the patient" })).toBeNull()   // no permission
  })

  it("emails a copy only when asked, to the address the server reports", async () => {
    perms.CLINIC_DOCUMENT_EMAIL = true
    render(<ReferralLetterModal consultationId="c1" onClose={() => {}} />)
    await issue()
    post.mockResolvedValueOnce({ data: { data: { recipient: "patient@example.org", number: "RF-2026-000004" } } })
    fireEvent.click(screen.getByRole("button", { name: "Email a copy to the patient" }))
    expect((await screen.findByRole("status")).textContent).toBe("Sent to patient@example.org.")
    expect(post.mock.calls[1][0]).toBe("/api/v1/clinic/patients/p1/issued-documents/d1/email")
  })

  it("shows why a copy could not be sent", async () => {
    perms.CLINIC_DOCUMENT_EMAIL = true
    render(<ReferralLetterModal consultationId="c1" onClose={() => {}} />)
    await issue()
    post.mockRejectedValueOnce({ response: { data: { message: "The patient has no usable email address on file. Add one to the profile first." } } })
    fireEvent.click(screen.getByRole("button", { name: "Email a copy to the patient" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/no usable email address/)
  })

  it("shows the server's message when issuing fails, read from the blob body", async () => {
    post.mockRejectedValue({ response: { data: new Blob([JSON.stringify({ message: "No practitioner on this visit" })]) } })
    render(<ReferralLetterModal consultationId="c1" onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText(/Reason for referral/), { target: { value: "x" } })
    fireEvent.click(screen.getByRole("button", { name: "Generate PDF" }))
    expect((await screen.findByRole("alert")).textContent).toBe("No practitioner on this visit")
  })
})
