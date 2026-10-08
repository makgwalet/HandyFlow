import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

const downloadPdf = vi.fn()
vi.mock("./patientFile.shared", async () => ({ ...(await vi.importActual<any>("./patientFile.shared")), downloadPdf: (...a: any[]) => downloadPdf(...a) }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
vi.mock("./ReferralLetterModal", () => ({ default: ({ consultationId }: any) => <div>referral for {consultationId}</div> }))
import QuickActionsCard from "./QuickActionsCard"

const older = { id: "c1", consultedAt: "2026-10-01T08:00:00Z", chiefComplaint: "Cough" }
const newer = { id: "c2", consultedAt: "2026-10-07T08:00:00Z", chiefComplaint: "Fever" }

afterEach(cleanup)
beforeEach(() => { downloadPdf.mockReset(); for (const k of Object.keys(perms)) delete perms[k] })

describe("quick actions", () => {
  it("shows nothing without a signing permission", () => {
    const { container } = render(<QuickActionsCard patientId="p1" consultations={[newer]} />)
    expect(container.textContent).toBe("")
  })
  it("shows only the actions the person may sign", () => {
    perms.CLINIC_SICK_NOTE_SIGN = true
    render(<QuickActionsCard patientId="p1" consultations={[newer]} />)
    expect(screen.getByRole("button", { name: /Sick note/ })).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Referral letter/ })).toBeNull()
  })
  it("switches both buttons off, with the reason, when there is no consultation", () => {
    perms.CLINIC_SICK_NOTE_SIGN = true; perms.CLINIC_REFERRAL_SIGN = true
    render(<QuickActionsCard patientId="p1" consultations={[]} />)
    expect((screen.getByRole("button", { name: /Sick note/ }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole("button", { name: /Referral letter/ }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/Needs a consultation/)).toBeTruthy()
  })
  it("writes the sick note for the newest consultation with the dates entered", async () => {
    perms.CLINIC_SICK_NOTE_SIGN = true; downloadPdf.mockResolvedValue(undefined)
    render(<QuickActionsCard patientId="p1" consultations={[older, newer]} />)
    fireEvent.click(screen.getByRole("button", { name: /Sick note/ }))
    fireEvent.change(screen.getByLabelText("Unfit from"), { target: { value: "2026-10-08" } })
    fireEvent.change(screen.getByLabelText("Unfit until"), { target: { value: "2026-10-10" } })
    fireEvent.click(screen.getByRole("button", { name: "Download sick note" }))
    await waitFor(() => expect(downloadPdf).toHaveBeenCalledWith("/api/v1/clinic/consultations/c2/medical-certificate?unfitFrom=2026-10-08&unfitTo=2026-10-10", "sick-note-p1.pdf"))
  })
  it("refuses an end date before the start date", async () => {
    perms.CLINIC_SICK_NOTE_SIGN = true
    render(<QuickActionsCard patientId="p1" consultations={[newer]} />)
    fireEvent.click(screen.getByRole("button", { name: /Sick note/ }))
    fireEvent.change(screen.getByLabelText("Unfit from"), { target: { value: "2026-10-10" } })
    fireEvent.change(screen.getByLabelText("Unfit until"), { target: { value: "2026-10-08" } })
    fireEvent.click(screen.getByRole("button", { name: "Download sick note" }))
    expect(await screen.findByText(/cannot be before/)).toBeTruthy()
    expect(downloadPdf).not.toHaveBeenCalled()
  })
  it("opens the referral letter straight away with one consultation, and asks which with several", () => {
    perms.CLINIC_REFERRAL_SIGN = true
    render(<QuickActionsCard patientId="p1" consultations={[newer]} />)
    fireEvent.click(screen.getByRole("button", { name: /Referral letter/ }))
    expect(screen.getByText("referral for c2")).toBeTruthy(); cleanup()
    render(<QuickActionsCard patientId="p1" consultations={[older, newer]} />)
    fireEvent.click(screen.getByRole("button", { name: /Referral letter/ }))
    fireEvent.change(screen.getByLabelText("Consultation *"), { target: { value: "c1" } })
    fireEvent.click(screen.getByRole("button", { name: "Continue" }))
    expect(screen.getByText("referral for c1")).toBeTruthy()
  })
})
