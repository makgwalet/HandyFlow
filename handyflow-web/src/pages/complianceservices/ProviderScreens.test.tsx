// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ProvidersPage from "./ProvidersPage"
import ClientProvidersTab from "./ClientProvidersTab"
import ProviderAssignmentCard from "./ProviderAssignmentCard"
import { assignmentProblem, conflictText, noteProblem, ratingProblem, ratingText } from "./providerRules"
import { linkFor, typeLabel } from "./queueRules"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let admin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => admin, usePermission: () => admin }))

const summary = { id: "p1", name: "Pat Safety", kind: "INDIVIDUAL", status: "ACTIVE", email: "pat@x.co", phone: null, coverage: "Gauteng", services: ["CS-FIRE"], usableAccreditations: 0, unchecked: 1, nextExpiry: null }
const accr = { id: "a1", body: "SAQA", reference: "R-1", issuedOn: null, expiresOn: "2027-01-01", status: "CLAIMED", expiry: "VALID", usable: false, recordedBy: "u1", checkedBy: null, checkNote: null, retireNote: null }
const assignment = (over: object = {}) => ({ id: "w1", providerId: "p1", providerName: "Pat Safety", clientId: "c1", clientName: "Acme", title: "Fire audit", description: null, dueDate: "2026-12-10", status: "PROPOSED", accreditationRequired: true, waiverNote: null, conflictStatus: "PENDING", conflictNote: null, conflictApproved: false, conflictApprovalNote: null, problemStarting: "A conflict declaration has not been recorded", rating: null, onTime: null, completionNote: null, cancelNote: null, ...over })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  admin = true
  get.mockReset(); post.mockReset(); put.mockReset()
  get.mockImplementation((url: string) => {
    if (url.endsWith("/providers")) return Promise.resolve({ data: { data: [summary] } })
    if (url.endsWith("/providers/p1")) return Promise.resolve({ data: { data: { provider: summary, notes: null, inactiveReason: null, accreditations: [accr] } } })
    if (url.endsWith("/providers/p1/performance")) return Promise.resolve({ data: { data: { providerId: "p1", completed: 0, onTime: 0, averageRating: null, cancelled: 0, open: 1 } } })
    if (url.endsWith("/providers/p1/assignments") || url.endsWith("/clients/c1/provider-assignments")) return Promise.resolve({ data: { data: [assignment()] } })
    return Promise.resolve({ data: { data: [] } })
  })
})
afterEach(cleanup)

describe("provider rules", () => {
  it("wording and checks", () => {
    expect(noteProblem("short")).not.toBeNull()
    expect(noteProblem("a long enough reason")).toBeNull()
    expect(ratingProblem(6)).not.toBeNull()
    expect(ratingProblem(4)).toBeNull()
    expect(ratingText(null, 0)).toBe("No finished work rated yet")
    expect(ratingText(4.25, 3)).toBe("4.3 out of 5")
    expect(conflictText({ conflictStatus: "DECLARED", conflictApproved: false })).toContain("waiting")
    expect(assignmentProblem({ providerId: "p", title: "x", accreditationRequired: false, waiverNote: "no" })).not.toBeNull()
    expect(assignmentProblem({ providerId: "p", title: "x", accreditationRequired: true, waiverNote: "" })).toBeNull()
  })
  it("queue labels and links", () => {
    expect(typeLabel("PROVIDER_WORK")).toBe("Provider work")
    expect(typeLabel("TENDER_CLOSING")).toBe("Tenders closing")
    expect(linkFor({ clientId: "c1", tab: "tenders", type: "TENDER_CLOSING" })).toBe("/complianceservices/clients/c1?tab=tenders")
    expect(linkFor({ clientId: null, tab: "providers", type: "PROVIDER_ACCREDITATION" })).toBe("/complianceservices/providers")
    expect(linkFor({ clientId: "c1", tab: "providers", type: "PROVIDER_WORK" })).toBe("/complianceservices/clients/c1?tab=providers")
  })
})

describe("providers page", () => {
  it("lists providers and says unchecked accreditation does not count", async () => {
    wrap(<ProvidersPage />)
    await waitFor(() => expect(screen.getByText("Pat Safety")).toBeTruthy())
    expect(document.body.textContent).toContain("1 waiting to be checked")
    expect(document.body.textContent).not.toContain("accredited")
  })
  it("checking an accreditation needs a note", async () => {
    post.mockResolvedValue({ data: { data: {} } })
    wrap(<ProvidersPage />)
    fireEvent.click(await screen.findByText("Pat Safety"))
    fireEvent.click(await screen.findByText("Check it"))
    fireEvent.click(screen.getByText("Save the check"))
    await waitFor(() => expect(document.body.textContent).toContain("at least 10 characters"))
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Check note"), { target: { value: "Looked it up on the SAQA register" } })
    fireEvent.click(screen.getByLabelText("The check passed"))
    fireEvent.click(screen.getByText("Save the check"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/provider-accreditations/a1/check", { accepted: true, note: "Looked it up on the SAQA register" }))
  })
  it("shows no rating when nothing is finished", async () => {
    wrap(<ProvidersPage />)
    fireEvent.click(await screen.findByText("Pat Safety"))
    await waitFor(() => expect(document.body.textContent).toContain("No finished work rated yet"))
  })
})

describe("client providers tab and assignment card", () => {
  it("proposing without a reason for no accreditation is refused", async () => {
    wrap(<ClientProvidersTab clientId="c1" />)
    fireEvent.click(await screen.findByText("Give work to a provider"))
    fireEvent.change(screen.getByLabelText("Provider"), { target: { value: "p1" } })
    fireEvent.change(screen.getByLabelText("What the work is"), { target: { value: "Fire audit" } })
    fireEvent.click(screen.getByLabelText("This work does not need an accredited provider"))
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(document.body.textContent).toContain("Say why no accreditation is needed"))
    expect(post).not.toHaveBeenCalled()
  })
  it("cannot start until conflict is recorded; recording needs a note", async () => {
    post.mockResolvedValue({ data: { data: { assignment: assignment({ conflictStatus: "NONE", problemStarting: null }), deliverables: [] } } })
    wrap(<ProviderAssignmentCard initial={{ assignment: assignment(), deliverables: [] }} />)
    expect(document.body.textContent).toContain("Cannot start yet")
    expect(screen.queryByText("Start the work")).toBeNull()
    fireEvent.click(screen.getByText("Record conflict declaration"))
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(document.body.textContent).toContain("at least 10 characters"))
    fireEvent.change(screen.getByLabelText("Note"), { target: { value: "No link to this provider" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/provider-assignments/w1/conflict", { conflict: false, note: "No link to this provider" }))
    await waitFor(() => expect(screen.getByText("Start the work")).toBeTruthy())
  })
  it("a declared conflict can only be approved by an administrator", () => {
    admin = false
    wrap(<ProviderAssignmentCard initial={{ assignment: assignment({ conflictStatus: "DECLARED", conflictNote: "Related", problemStarting: "waiting" }), deliverables: [] }} />)
    expect(screen.queryByText("Approve conflict")).toBeNull()
  })
  it("deliverables say handed over, never delivered", () => {
    wrap(<ProviderAssignmentCard initial={{ assignment: assignment({ status: "ACTIVE", conflictStatus: "NONE", problemStarting: null }), deliverables: [{ id: "d1", title: "Report", dueDate: null, status: "RECEIVED", receivedBy: "u1", reviewedBy: null, reviewNote: null }] }} />)
    expect(document.body.textContent).toContain("Handed over, to be reviewed")
    expect(document.body.textContent).not.toMatch(/delivered|read/i)
  })
})
