// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvProgrammeNeeds from "./TrainProvProgrammeNeeds"
import TrainProvCohortReadiness from "./TrainProvCohortReadiness"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const need = (over: object = {}) => ({
  id: "n1", programmeVersionId: "v1", resourceType: "EQUIPMENT", description: "Arc welding machine", matchText: null, quantity: 2, perLearners: 4,
  sourceNote: "Listed in the programme's own curriculum", status: "ACTIVE", withdrawnReason: null, createdAt: "", ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
let editable = true
let needs: unknown[] = []

beforeEach(() => {
  get.mockReset(); post.mockReset(); editable = true; needs = [need()]
  get.mockImplementation((url: string) => Promise.resolve({
    data: { data: /resource-readiness$/.test(url)
      ? { cohortId: "c1", siteName: "Main campus", learners: 10, note: "A comparison of the site register with the programme's own list. It is a prompt to check and does not show that the programme meets any standard.",
          rows: [{ needId: "n1", description: "Arc welding machine", resourceType: "EQUIPMENT", required: 6, held: 4, status: "SHORT", note: "The site register lists 4 in service; the programme lists 6. Check before the cohort starts" }] }
      : { versionId: "v1", versionNo: 1, versionStatus: editable ? "DRAFT" : "APPROVED", editable, needs } },
  }))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("programme resource needs", () => {
  it("shows a need with where it comes from", async () => {
    wrap(<TrainProvProgrammeNeeds versionId="v1" canManage />)
    expect(await screen.findByText("2 × Arc welding machine (Equipment) for every 4 learners")).toBeTruthy()
    expect(screen.getByText(/Source: Listed in the programme's own curriculum/)).toBeTruthy()
  })

  it("says plainly when none are listed", async () => {
    needs = []
    wrap(<TrainProvProgrammeNeeds versionId="v1" canManage />)
    expect(await screen.findByText(/That does not mean none are needed/)).toBeTruthy()
  })

  it("freezes an approved version", async () => {
    editable = false
    wrap(<TrainProvProgrammeNeeds versionId="v1" canManage />)
    expect(await screen.findByText(/its list is frozen/)).toBeTruthy()
    expect(screen.queryByText("Add a resource need")).toBeNull()
    expect(screen.queryByText("Withdraw")).toBeNull()
  })

  it("will not save without a source note", async () => {
    needs = []
    wrap(<TrainProvProgrammeNeeds versionId="v1" canManage />)
    fireEvent.click(await screen.findByText("Add a resource need"))
    fireEvent.change(screen.getByLabelText("Need type"), { target: { value: "EQUIPMENT" } })
    fireEvent.change(screen.getByLabelText("What is needed"), { target: { value: "Arc welding machine" } })
    fireEvent.click(screen.getByText("Save need"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    expect(post).not.toHaveBeenCalled()
  })

  it("adds a need", async () => {
    needs = []
    wrap(<TrainProvProgrammeNeeds versionId="v1" canManage />)
    fireEvent.click(await screen.findByText("Add a resource need"))
    fireEvent.change(screen.getByLabelText("Need type"), { target: { value: "EQUIPMENT" } })
    fireEvent.change(screen.getByLabelText("What is needed"), { target: { value: "Arc welding machine" } })
    fireEvent.change(screen.getByLabelText("Where does the need come from?"), { target: { value: "Listed in the programme's own curriculum" } })
    fireEvent.click(screen.getByText("Save need"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/programme-versions\/v1\/resource-needs$/), expect.objectContaining({ resourceType: "EQUIPMENT", quantity: 1, perLearners: null })))
  })

  it("needs a reason to withdraw", async () => {
    wrap(<TrainProvProgrammeNeeds versionId="v1" canManage />)
    fireEvent.click(await screen.findByText("Withdraw"))
    fireEvent.click(screen.getByText("Withdraw need"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    fireEvent.change(screen.getByLabelText("Why is it withdrawn?"), { target: { value: "Replaced by a new line" } })
    fireEvent.click(screen.getByText("Withdraw need"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/resource-needs\/n1\/withdraw$/), { reason: "Replaced by a new line" }))
  })
})

describe("cohort readiness", () => {
  it("is a prompt to check and never a finding", async () => {
    wrap(<TrainProvCohortReadiness cohortId="c1" />)
    fireEvent.click(screen.getByText("Resources compared with the programme"))
    expect(await screen.findByText(/does not show that the programme meets any standard/)).toBeTruthy()
    expect(await screen.findByText(/Check before the cohort starts/)).toBeTruthy()
    expect(screen.getByText("Fewer than listed")).toBeTruthy()
  })
})
