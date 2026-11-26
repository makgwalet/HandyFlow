// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvProgrammeHours from "./TrainProvProgrammeHours"

const get = vi.fn()
const put = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a), post: (...a: unknown[]) => post(...a) } }))

const hours = (over: object = {}) => ({
  id: "h1", programmeVersionId: "v1", hours: 120, covers: null, sourceType: "CURRICULUM", sourceReference: "Curriculum document", sourceVersionDate: null, sourceSection: "Section 4",
  effectiveDate: "2020-02-01", verifiedBy: "Sam Dube", verifiedOn: "2020-03-01", status: "ACTIVE", withdrawnReason: null, updatedAt: "2020-03-01T08:00:00Z", ...over,
})
const state = (over: object = {}) => ({ programmeVersionId: "v1", versionNo: 1, versionStatus: "DRAFT", editable: true, current: null, history: [], ...over })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

let current: unknown = state()
beforeEach(() => {
  get.mockReset(); put.mockReset(); post.mockReset(); current = state()
  get.mockImplementation(() => Promise.resolve({ data: { data: current } }))
  put.mockResolvedValue({ data: { data: {} } }); post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("programme workplace hours", () => {
  it("says none are recorded and that this does not mean none are needed", async () => {
    wrap(<TrainProvProgrammeHours versionId="v1" canManage />)
    expect(await screen.findByText(/records no workplace hours\. That does not mean none are needed/)).toBeTruthy()
  })

  it("will not save hours without their source", async () => {
    wrap(<TrainProvProgrammeHours versionId="v1" canManage />)
    fireEvent.click(await screen.findByText("Record workplace hours"))
    fireEvent.change(screen.getByLabelText("Hours"), { target: { value: "120" } })
    fireEvent.click(screen.getByText("Save hours"))
    expect(await screen.findByText("Say what kind of source the hours come from")).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
  })

  it("saves hours with the whole source note", async () => {
    wrap(<TrainProvProgrammeHours versionId="v1" canManage />)
    fireEvent.click(await screen.findByText("Record workplace hours"))
    fireEvent.change(screen.getByLabelText("Hours"), { target: { value: "120" } })
    fireEvent.change(screen.getByLabelText("Kind of source"), { target: { value: "CURRICULUM" } })
    fireEvent.change(screen.getByLabelText("Document or reference"), { target: { value: "Curriculum document" } })
    fireEvent.change(screen.getByLabelText("Section or page"), { target: { value: "Section 4" } })
    fireEvent.change(screen.getByLabelText("Takes effect on"), { target: { value: "2020-02-01" } })
    fireEvent.change(screen.getByLabelText("Checked by"), { target: { value: "Sam Dube" } })
    fireEvent.change(screen.getByLabelText("Checked on"), { target: { value: "2020-03-01" } })
    fireEvent.click(screen.getByText("Save hours"))
    await waitFor(() => expect(put).toHaveBeenCalledWith(expect.stringMatching(/programme-versions\/v1\/workplace-hours$/), expect.objectContaining({ hours: 120, sourceType: "CURRICULUM", sourceSection: "Section 4", verifiedBy: "Sam Dube" })))
  })

  it("shows the figure with its source and no way to change it once the version is approved", async () => {
    current = state({ versionStatus: "APPROVED", editable: false, current: hours(), history: [hours()] })
    wrap(<TrainProvProgrammeHours versionId="v1" canManage />)
    expect(await screen.findByText(/120 workplace hours\. Source: Curriculum, Curriculum document, Section 4/)).toBeTruthy()
    expect(screen.getByText(/hours are frozen/)).toBeTruthy()
    expect(screen.queryByText("Change hours")).toBeNull()
    expect(screen.queryByText("Withdraw hours")).toBeNull()
  })

  it("needs a reason to withdraw and keeps the withdrawn record visible", async () => {
    current = state({ current: hours(), history: [hours(), hours({ id: "h0", status: "WITHDRAWN", hours: 80, withdrawnReason: "Entered against the wrong version" })] })
    wrap(<TrainProvProgrammeHours versionId="v1" canManage />)
    fireEvent.click(await screen.findByText("Withdraw hours"))
    fireEvent.click(screen.getByText("Withdraw hours"))
    expect(await screen.findByText(/reason of at least 10/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Cancel"))
    fireEvent.click(screen.getByText("Withdrawn (1)"))
    expect(await screen.findByText(/Withdrawn: Entered against the wrong version/)).toBeTruthy()
  })
})
