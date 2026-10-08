import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a) } }))
import ReferralLetterModal from "./ReferralLetterModal"

beforeEach(() => {
  post.mockReset()
  ;(URL as any).createObjectURL = vi.fn(() => "blob:x"); (URL as any).revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
})
afterEach(cleanup)

describe("ReferralLetterModal", () => {
  it("needs a reason before it will generate", () => {
    render(<ReferralLetterModal consultationId="c1" onClose={() => {}} />)
    expect((screen.getByRole("button", { name: "Generate PDF" }) as HTMLButtonElement).disabled).toBe(true)
  })
  it("posts the form for the consultation and closes", async () => {
    post.mockResolvedValue({ data: new Blob(["x"]) })
    const onClose = vi.fn()
    render(<ReferralLetterModal consultationId="c1" onClose={onClose} />)
    fireEvent.change(screen.getByLabelText(/Reason for referral/), { target: { value: "Chest pain work-up" } })
    fireEvent.change(screen.getByLabelText("Urgency"), { target: { value: "URGENT" } })
    fireEvent.click(screen.getByRole("button", { name: "Generate PDF" }))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/clinic/consultations/c1/referral-letter")
    expect(post.mock.calls[0][2].params).toMatchObject({ reason: "Chest pain work-up", urgency: "URGENT" })
  })
  it("shows the server's message when it fails", async () => {
    post.mockRejectedValue({ response: { data: { message: "No practitioner on this visit" } } })
    render(<ReferralLetterModal consultationId="c1" onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText(/Reason for referral/), { target: { value: "x" } })
    fireEvent.click(screen.getByRole("button", { name: "Generate PDF" }))
    expect((await screen.findByRole("alert")).textContent).toBe("No practitioner on this visit")
  })
})
