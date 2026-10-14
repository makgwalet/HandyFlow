import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { AuditorAccessSection, defaultExpiry } from "./AuditorAccessSection"

const api = vi.hoisted(() => ({ list: vi.fn(), invite: vi.fn(), revoke: vi.fn(), changeExpiry: vi.fn() }))
vi.mock("../../api/auditor.api", () => ({ auditorApi: api }))
afterEach(cleanup)
beforeEach(() => { Object.values(api).forEach(f => f.mockReset()); api.list.mockResolvedValue([]) })

const grant = (o: any) => ({ id: "g1", inviteEmail: "a@firm.test", status: "ACTIVE", invitedAt: "2026-09-01T00:00:00Z", expiresAt: "2026-12-31T00:00:00Z", scopeNote: "FY2026 audit", expired: false, ...o })

describe("AuditorAccessSection", () => {
  it("offers 90 days by default and sends the chosen date and scope with the invite", async () => {
    api.invite.mockResolvedValue(grant({}))
    render(<AuditorAccessSection />)
    expect((await screen.findByLabelText("Access ends on") as HTMLInputElement).value).toBe(defaultExpiry())
    fireEvent.change(screen.getByLabelText("Auditor's email"), { target: { value: "x@firm.test" } })
    fireEvent.change(screen.getByLabelText(/Your business name/), { target: { value: "Acme" } })
    fireEvent.change(screen.getByLabelText("Access ends on"), { target: { value: "2027-02-01" } })
    fireEvent.change(screen.getByLabelText(/What they are auditing/), { target: { value: "Year-end" } })
    fireEvent.click(screen.getByText("Send Invite"))
    await waitFor(() => expect(api.invite).toHaveBeenCalledWith("x@firm.test", "Acme", "2027-02-01", "Year-end"))
  })

  it("shows when access ends, marks an expired grant, and lets the owner change the date", async () => {
    api.list.mockResolvedValue([grant({ expired: true })])
    api.changeExpiry.mockResolvedValue(grant({}))
    render(<AuditorAccessSection />)
    expect(await screen.findByText("EXPIRED")).toBeTruthy()
    expect(screen.getByText("FY2026 audit")).toBeTruthy()
    fireEvent.click(screen.getByText("Change"))
    fireEvent.change(screen.getByLabelText("New end date"), { target: { value: "2027-03-15" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(api.changeExpiry).toHaveBeenCalledWith("g1", "2027-03-15"))
  })
})
