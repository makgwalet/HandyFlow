import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a) } }))
import { VerifyDocumentPage } from "./VerifyDocumentPage"

const show = (url = "/verify-document") => render(<MemoryRouter initialEntries={[url]}><VerifyDocumentPage /></MemoryRouter>)
const CODE = "ABCD-EFGH-JKMN-PQRS"
beforeEach(() => post.mockReset())
afterEach(cleanup)

describe("VerifyDocumentPage", () => {
  it("keeps Check switched off until the code is complete", () => {
    show()
    const btn = screen.getByRole("button", { name: "Check" }) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Verification code"), { target: { value: "abcdefghjkmnpqrs" } })
    expect(btn.disabled).toBe(false)
    expect((screen.getByLabelText("Verification code") as HTMLInputElement).value).toBe(CODE)
  })
  it("posts the code in the body and shows the document details, and nothing about a patient", async () => {
    post.mockResolvedValue({ data: { data: { valid: true, voided: false, documentType: "Letter", documentNumber: "LT-2026-000042", issuedOn: "13 October 2026", issuedBy: "Sample Practice" } } })
    show(`/verify-document?code=${CODE}`)
    fireEvent.click(screen.getByRole("button", { name: "Check" }))
    expect(await screen.findByText("This document was issued by the practice.")).toBeTruthy()
    expect(post).toHaveBeenCalledWith("/api/v1/public/clinic/document-verification", { code: CODE })
    expect(screen.getByText("Letter LT-2026-000042")).toBeTruthy()
    expect(screen.getByText("13 October 2026")).toBeTruthy()
    expect(screen.getByText(/does not show the document in your hand is unchanged/)).toBeTruthy()
  })
  it("tells the reader a withdrawn document is not to be relied on", async () => {
    post.mockResolvedValue({ data: { data: { valid: true, voided: true, documentType: "Letter", documentNumber: "LT-2026-000001", issuedOn: "1 October 2026", issuedBy: "Sample Practice" } } })
    show(`/verify-document?code=${CODE}`)
    fireEvent.click(screen.getByRole("button", { name: "Check" }))
    expect(await screen.findByText(/has since been withdrawn/)).toBeTruthy()
  })
  it("says so when no document matches, with no details", async () => {
    post.mockResolvedValue({ data: { data: { valid: false, voided: false } } })
    show(`/verify-document?code=${CODE}`)
    fireEvent.click(screen.getByRole("button", { name: "Check" }))
    expect(await screen.findByText(/No document matches that code/)).toBeTruthy()
    expect(screen.queryByText("Issued by")).toBeNull()
  })
  it("explains a rate limit and a failed check", async () => {
    post.mockRejectedValueOnce({ response: { status: 429 } })
    show(`/verify-document?code=${CODE}`)
    fireEvent.click(screen.getByRole("button", { name: "Check" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Too many checks/)
    post.mockRejectedValueOnce(new Error("network"))
    fireEvent.click(screen.getByRole("button", { name: "Check" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/could not be made/))
  })
})
