import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { AuditorAccessSection, defaultExpiry } from "./AuditorAccessSection"

const api = vi.hoisted(() => ({ list: vi.fn(), invite: vi.fn(), revoke: vi.fn(), changeExpiry: vi.fn(), engagements: vi.fn(), createEngagement: vi.fn(), engagementAction: vi.fn(), manifest: vi.fn() }))
vi.mock("../../api/auditor.api", () => ({ auditorApi: api }))
vi.mock("../../api/auditQueries.api", () => ({ businessAuditQueryApi: { list: () => Promise.resolve([]) } }))
vi.mock("../../api/evidenceRequests.api", () => ({ businessEvidenceRequestApi: { list: () => Promise.resolve([]) } }))
afterEach(cleanup)
beforeEach(() => { Object.values(api).forEach(f => f.mockReset()); api.list.mockResolvedValue([]); api.engagements.mockResolvedValue([]) })

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
    await waitFor(() => expect(api.invite).toHaveBeenCalledWith("x@firm.test", "Acme", "2027-02-01", "Year-end", undefined))
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

  const eng = (o: any) => ({ id: "e1", name: "FY2026 audit", firmName: "Firm LLP", periodFrom: "2025-03-01", periodTo: "2026-02-28", status: "PLANNED", ...o })

  it("attaches an invite to the chosen engagement, offering only ones that can still take auditors", async () => {
    api.engagements.mockResolvedValue([eng({}), eng({ id: "e2", name: "Old audit", status: "ARCHIVED" })])
    api.invite.mockResolvedValue(grant({}))
    render(<AuditorAccessSection />)
    const select = (await screen.findByLabelText("Engagement (optional)")) as HTMLSelectElement
    await waitFor(() => expect(select.options.length).toBe(2))
    expect(Array.from(select.options).some(o => /Old audit/.test(o.text))).toBe(false)
    fireEvent.change(select, { target: { value: "e1" } })
    fireEvent.change(screen.getByLabelText("Auditor's email"), { target: { value: "x@firm.test" } })
    fireEvent.change(screen.getByLabelText(/Your business name/), { target: { value: "Acme" } })
    fireEvent.click(screen.getByText("Send Invite"))
    await waitFor(() => expect(api.invite).toHaveBeenCalledWith("x@firm.test", "Acme", defaultExpiry(), "", "e1"))
  })

  it("creates an engagement only with a name, firm and a period that runs forwards", async () => {
    api.createEngagement.mockResolvedValue(eng({}))
    render(<AuditorAccessSection />)
    fireEvent.click(await screen.findByText("New engagement"))
    const save = screen.getByText("Save engagement") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Engagement name"), { target: { value: "FY2026 audit" } })
    fireEvent.change(screen.getByLabelText("Audit firm"), { target: { value: "Firm LLP" } })
    fireEvent.change(screen.getByLabelText("Period from"), { target: { value: "2026-02-28" } })
    fireEvent.change(screen.getByLabelText("Period to"), { target: { value: "2025-03-01" } })
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Period from"), { target: { value: "2025-03-01" } })
    fireEvent.change(screen.getByLabelText("Period to"), { target: { value: "2026-02-28" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(api.createEngagement).toHaveBeenCalledWith({ name: "FY2026 audit", firmName: "Firm LLP", periodFrom: "2025-03-01", periodTo: "2026-02-28", entitiesInScope: undefined }))
  })

  it("asks before finishing an engagement, since that cuts the auditors' access", async () => {
    api.engagements.mockResolvedValue([eng({ status: "ACTIVE" })])
    api.engagementAction.mockResolvedValue(eng({ status: "COMPLETED" }))
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true)
    render(<AuditorAccessSection />)
    fireEvent.click(await screen.findByText("Finish"))
    expect(api.engagementAction).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Finish"))
    await waitFor(() => expect(api.engagementAction).toHaveBeenCalledWith("e1", "complete"))
    confirmSpy.mockRestore()
  })
})
