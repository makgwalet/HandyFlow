import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"

vi.mock("../../api/auditMyTasks.api", () => ({ auditMyTasksApi: { mine: vi.fn() } }))
import { auditMyTasksApi } from "../../api/auditMyTasks.api"
import { MyTasksSection } from "./MyTasksSection"

const m = auditMyTasksApi as unknown as { mine: ReturnType<typeof vi.fn> }
afterEach(cleanup)
beforeEach(() => vi.clearAllMocks())
const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter>
  <Routes><Route path="/" element={<MyTasksSection today="2026-10-28" />} /><Route path="/internal-audit/engagements/:id" element={<div>engagement page</div>} /></Routes></MemoryRouter></QueryClientProvider>)
const task = (o: object) => ({ kind: "ACTION", id: "a", engagementId: "e1", title: "Retrain approvers", context: "Late approvals · Payroll audit", severity: null, dueDate: "2026-10-20", overdue: true, daysOverdue: 8, ...o })

describe("My tasks", () => {
  it("groups tasks, shows how late, and opens the engagement", async () => {
    m.mine.mockResolvedValue({ summary: { total: 3, overdue: 1, dueThisWeek: 1 }, tasks: [task({}), task({ kind: "ACTION_VALIDATE", id: "v", title: "Check the training", overdue: false, daysOverdue: 0, dueDate: "2026-10-30" }), task({ kind: "NOTE_ANSWER", id: "n", title: "Explain variance", dueDate: null, overdue: false, daysOverdue: 0 })] })
    mount()
    expect(await screen.findByText("8 days late")).toBeTruthy()
    expect(screen.getByText("Validate")).toBeTruthy()
    expect(screen.getByText("No due date (1)")).toBeTruthy()
    fireEvent.click(screen.getByText("Retrain approvers"))
    expect(await screen.findByText("engagement page")).toBeTruthy()
  })

  it("says so when nothing is waiting, and when the load fails", async () => {
    m.mine.mockResolvedValue({ summary: { total: 0, overdue: 0, dueThisWeek: 0 }, tasks: [] })
    mount()
    expect(await screen.findByText(/You have nothing waiting on you/)).toBeTruthy()
    cleanup(); m.mine.mockRejectedValue(new Error("x"))
    mount()
    expect((await screen.findByRole("alert")).textContent).toContain("could not be loaded")
  })
})
