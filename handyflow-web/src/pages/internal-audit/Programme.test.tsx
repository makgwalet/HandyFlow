import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"

vi.mock("../../api/auditProgramme.api", () => ({ auditProgrammeApi: { library: vi.fn(), get: vi.fn(), apply: vi.fn(), record: vi.fn(), link: vi.fn(), engagements: vi.fn(), findings: vi.fn() } }))
import { auditProgrammeApi } from "../../api/auditProgramme.api"
import { ProgrammeSection } from "./ProgrammeSection"

const m = auditProgrammeApi as any
afterEach(cleanup)

const counts = (x: any = {}) => ({ total: 2, completed: 1, notApplicable: 0, inProgress: 0, notStarted: 1, ineffective: 0, partial: 0, needingFinding: 0, donePct: 50, ...x })
const step = (x: any = {}) => ({ controlCode: "IT-01", domain: "IT", area: "Access management", objective: "Only authorised staff have access.", procedure: "Compare the user list.", reference: "ISO/IEC 27001:2022 A.5.15", status: "NOT_STARTED", needsFinding: false, ...x })
const prog = (steps = [step()], x: any = {}) => ({ engagementId: "g1", overall: counts(), byDomain: { IT: counts() }, steps, ...x })
const lib = { domains: { IT: "IT general controls", CYBER: "Cyber security" }, controls: [{ code: "IT-01", domain: "IT", area: "a", objective: "o", procedure: "p" }, { code: "CY-01", domain: "CYBER", area: "a", objective: "o", procedure: "p" }], notice: "Not legal advice." }

beforeEach(() => {
  vi.clearAllMocks()
  m.engagements.mockResolvedValue([{ id: "g1", name: "IT review" }])
  m.library.mockResolvedValue(lib)
  m.get.mockResolvedValue(prog())
  m.findings.mockResolvedValue([{ id: "f1", title: "Leavers still active" }])
})
const choose = async () => { render(<ProgrammeSection />); fireEvent.change(await screen.findByLabelText("Engagement"), { target: { value: "g1" } }); await screen.findByText(/Only authorised staff/) }

describe("Programme library", () => {
  it("shows progress and the check wording with its reference", async () => {
    await choose()
    expect(screen.getByText(/Compare the user list\. \(ISO\/IEC 27001:2022 A\.5\.15\)/)).toBeTruthy()
    expect(screen.getByText("50%")).toBeTruthy()
  })

  it("adds whole domains and shows the notice", async () => {
    m.apply.mockResolvedValue(prog())
    await choose()
    expect(screen.getByText("Not legal advice.")).toBeTruthy()
    const add = screen.getByText("Add to programme") as HTMLButtonElement
    expect(add.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("Cyber security"))
    fireEvent.click(add)
    await waitFor(() => expect(m.apply).toHaveBeenCalledWith("g1", ["CYBER"], []))
  })

  it("will not complete a step without a note, then saves with a conclusion", async () => {
    m.record.mockResolvedValue(prog([step({ status: "COMPLETED", conclusion: "INEFFECTIVE", note: "Three leavers active.", needsFinding: true })]))
    await choose()
    fireEvent.click(screen.getByText("Start IT-01"))
    fireEvent.change(screen.getByLabelText("Status of IT-01"), { target: { value: "COMPLETED" } })
    const save = screen.getByText("Save") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Conclusion for IT-01"), { target: { value: "INEFFECTIVE" } })
    fireEvent.change(screen.getByLabelText("Note for IT-01"), { target: { value: "Three leavers active." } })
    fireEvent.click(save)
    await waitFor(() => expect(m.record).toHaveBeenCalledWith("g1", "IT-01", "COMPLETED", "INEFFECTIVE", "Three leavers active."))
    expect(await screen.findByText("No finding raised for this result.")).toBeTruthy()
  })

  it("a step in progress can be saved without a note", async () => {
    m.record.mockResolvedValue(prog([step({ status: "IN_PROGRESS" })]))
    await choose()
    fireEvent.click(screen.getByText("Start IT-01"))
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(m.record).toHaveBeenCalledWith("g1", "IT-01", "IN_PROGRESS", null, ""))
  })

  it("links a finding to a weak result", async () => {
    m.get.mockResolvedValue(prog([step({ status: "COMPLETED", conclusion: "INEFFECTIVE", needsFinding: true, note: "Three leavers active." })]))
    m.link.mockResolvedValue(prog())
    await choose()
    fireEvent.change(screen.getByLabelText("Finding for IT-01"), { target: { value: "f1" } })
    await waitFor(() => expect(m.link).toHaveBeenCalledWith("g1", "IT-01", "f1"))
  })

  it("shows the server's message when saving fails", async () => {
    m.record.mockRejectedValue({ response: { data: { message: "This engagement is closed" } } })
    await choose()
    fireEvent.click(screen.getByText("Start IT-01"))
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByText("This engagement is closed")).toBeTruthy()
  })
})
