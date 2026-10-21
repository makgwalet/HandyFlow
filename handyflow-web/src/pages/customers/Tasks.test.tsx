import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TaskQueue from "./TaskQueue"
import { addDays, assigneeText, daysLate, dueText, filterTasks, groupTasks, isEscalated, newTaskBody, newTaskProblem, sectionOf, snoozeDate, taskTotals, DEFAULT_FILTER, type Task } from "./tasks.logic"
import { localToday } from "./pipeline.logic"

const get = vi.fn(), post = vi.fn(), patch = vi.fn(), del = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), patch: (...a: Args) => patch(...a), delete: (...a: Args) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: { user: { userId: string } }) => unknown) => sel({ user: { userId: "me" } }) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const TODAY = localToday()
const task = (id: string, customerName: string, offset: number, o: Partial<Task> = {}): Task => ({ id, customerId: `c-${id}`, customerName, dueDate: addDays(TODAY, offset), note: `Call ${customerName}`, assignedTo: "me", priority: "NORMAL", completed: false, ...o })
const tasks: Task[] = [
  task("a", "Acme Mining", -5, { priority: "HIGH" }), task("b", "Bravo Foods", -1), task("c", "Cape Wines", 0), task("d", "Delta Haulage", 3, { assignedTo: "other" }), task("e", "Echo Retail", 30, { priority: "LOW" }),
]
const users = [{ id: "me", firstName: "Me", lastName: "Self" }, { id: "other", firstName: "Olive", lastName: "Other" }]
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TaskQueue /></QueryClientProvider>)
beforeEach(() => {
  perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); for (const m of [get, post, patch, del]) m.mockReset()
  get.mockImplementation((url: string) => (url.includes("identity/users") ? ok({ content: users }) : url.includes("/crm/tasks") ? ok({ tasks, truncated: false }) : ok({ content: [] })))
  for (const m of [post, patch, del]) m.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("task rules", () => {
  it("puts tasks in sections by due date and says how late", () => {
    expect(tasks.map(t => sectionOf(t, TODAY))).toEqual(["overdue", "overdue", "today", "week", "later"])
    expect(daysLate(tasks[0], TODAY)).toBe(5); expect(daysLate(tasks[2], TODAY)).toBe(0); expect(daysLate({ ...tasks[0], completed: true }, TODAY)).toBe(0)
    expect(dueText(tasks[0], TODAY)).toBe("5 days late"); expect(dueText(tasks[1], TODAY)).toBe("1 day late"); expect(dueText(tasks[2], TODAY)).toBe("Due today"); expect(dueText(task("x", "X", 1), TODAY)).toBe("Due tomorrow"); expect(dueText(tasks[3], TODAY)).toBe("Due in 3 days")
  })
  it("flags a task as escalated once it is three days late", () => {
    expect(isEscalated(tasks[0], TODAY)).toBe(true); expect(isEscalated(tasks[1], TODAY)).toBe(false); expect(isEscalated(task("x", "X", -3), TODAY)).toBe(true)
  })
  it("sorts the sections: most late first, then high priority", () => {
    const g = groupTasks([task("p", "P", 2), task("q", "Q", 2, { priority: "HIGH" }), ...tasks], TODAY)
    expect(g.map(x => x.section)).toEqual(["overdue", "today", "week", "later"]); expect(g[0].tasks.map(t => t.id)).toEqual(["a", "b"]); expect(g[2].tasks.map(t => t.id)).toEqual(["q", "p", "d"])
  })
  it("totals, filters and names assignees", () => {
    expect(taskTotals(tasks, TODAY)).toEqual({ open: 5, overdue: 2, today: 1, week: 1, escalated: 1, high: 1 })
    expect(filterTasks(tasks, DEFAULT_FILTER, "me").map(t => t.id)).toEqual(["a", "b", "c", "e"]); expect(filterTasks(tasks, { ...DEFAULT_FILTER, scope: "all", priority: "LOW" }, "me").map(t => t.id)).toEqual(["e"])
    expect(filterTasks(tasks, { ...DEFAULT_FILTER, scope: "all", search: " cape" }, "me").map(t => t.id)).toEqual(["c"]); expect(filterTasks(tasks, DEFAULT_FILTER, null)).toEqual([])
    expect(assigneeText(null, "me", {})).toBe("Unassigned"); expect(assigneeText("me", "me", {})).toBe("You"); expect(assigneeText("o", "me", { o: "Olive" })).toBe("Olive"); expect(assigneeText("z", "me", {})).toBe("Colleague")
  })
  it("snoozes count from today, so a late task comes back soon", () => {
    expect(snoozeDate("tomorrow", "2026-10-31")).toBe("2026-11-01"); expect(snoozeDate("three-days", "2026-12-30")).toBe("2027-01-02"); expect(snoozeDate("next-week", "2026-02-25")).toBe("2026-03-04")
  })
  it("checks a new task and sends the fields the server needs", () => {
    const t = { customerId: "c1", note: " Send quote ", dueDate: TODAY, priority: "HIGH" as const, assignedTo: "me" }
    expect(newTaskProblem({ ...t, customerId: "" }, TODAY)).toMatch(/customer/); expect(newTaskProblem({ ...t, note: "  " }, TODAY)).toMatch(/what needs/i); expect(newTaskProblem({ ...t, dueDate: addDays(TODAY, -1) }, TODAY)).toMatch(/past/); expect(newTaskProblem({ ...t, dueDate: "2026-02-30" }, TODAY)).toMatch(/due date/); expect(newTaskProblem(t, TODAY)).toBeNull()
    expect(newTaskBody(t)).toEqual({ dueDate: TODAY, note: "Send quote", priority: "HIGH", assignedTo: "me" })
  })
})

describe("task queue", () => {
  it("shows my tasks in sections, flags the escalated one, and everyone's on request", async () => {
    wrap(); const overdue = await screen.findByLabelText("Overdue tasks"); expect(within(overdue).getByLabelText("Acme Mining: Call Acme Mining").textContent).toContain("ESCALATED"); expect(within(overdue).getByLabelText("Bravo Foods: Call Bravo Foods").textContent).not.toContain("ESCALATED")
    expect(screen.queryByLabelText("Delta Haulage: Call Delta Haulage")).toBeNull(); expect(screen.getByLabelText("Task totals").textContent).toMatch(/2\s*overdue/)
    fireEvent.change(screen.getByLabelText("Whose tasks"), { target: { value: "all" } }); expect(screen.getByLabelText("Delta Haulage: Call Delta Haulage").textContent).toContain("Olive Other")
  })
  it("finishes a task through the follow-up endpoint, with its outcome", async () => {
    wrap(); fireEvent.click(await screen.findByLabelText("Done: Acme Mining")); await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customers/c-a/followups/a/complete", { outcome: "COMPLETED" }))
    fireEvent.click(screen.getByLabelText("No response: Bravo Foods")); await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customers/c-b/followups/b/complete", { outcome: "NO_RESPONSE" }))
  })
  it("snoozes to a preset date, reassigns and re-prioritises through the queue endpoint", async () => {
    wrap(); fireEvent.change(await screen.findByLabelText("Snooze Acme Mining"), { target: { value: "tomorrow" } }); await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/tasks/a", { dueDate: addDays(TODAY, 1) }))
    fireEvent.change(screen.getByLabelText("Assign Acme Mining"), { target: { value: "other" } }); await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/tasks/a", { assignedTo: "other" }))
    fireEvent.change(screen.getByLabelText("Priority Bravo Foods"), { target: { value: "HIGH" } }); await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/tasks/b", { priority: "HIGH" }))
  })
  it("snoozes to a picked date and refuses a past one", async () => {
    wrap(); fireEvent.change(await screen.findByLabelText("Snooze Acme Mining"), { target: { value: "pick" } })
    fireEvent.change(screen.getByLabelText("New due date"), { target: { value: addDays(TODAY, -2) } }); expect((screen.getByText("Move") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("New due date"), { target: { value: addDays(TODAY, 10) } }); fireEvent.click(screen.getByText("Move")); await waitFor(() => expect(patch).toHaveBeenCalledWith("/api/v1/crm/tasks/a", { dueDate: addDays(TODAY, 10) }))
  })
  it("deletes only after a confirm", async () => {
    wrap(); fireEvent.click(await screen.findByLabelText("Delete: Acme Mining")); expect(del).not.toHaveBeenCalled(); fireEvent.click(screen.getByLabelText("Confirm delete: Acme Mining")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/customers/c-a/followups/a"))
  })
  it("adds a task: finds the customer, then posts to that customer's follow-ups", async () => {
    get.mockImplementation((url: string) => (url.includes("identity/users") ? ok({ content: users }) : url.includes("/crm/tasks") ? ok({ tasks, truncated: false }) : ok({ content: [{ id: "cust1", name: "Zulu Trading" }] })))
    wrap(); fireEvent.click(await screen.findByText("New task")); const form = screen.getByRole("form", { name: "New task" })
    fireEvent.change(within(form).getByLabelText("Find customer"), { target: { value: "zulu" } }); fireEvent.click(within(form).getByText("Search")); fireEvent.click(await within(form).findByText("Zulu Trading"))
    fireEvent.change(within(form).getByLabelText("What needs doing"), { target: { value: "Send the quote" } }); fireEvent.change(within(form).getByLabelText("New task priority"), { target: { value: "HIGH" } }); fireEvent.click(within(form).getByText("Add task"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customers/cust1/followups", { dueDate: TODAY, note: "Send the quote", priority: "HIGH", assignedTo: "me" }))
  })
  it("will not add a task without a customer", async () => {
    wrap(); fireEvent.click(await screen.findByText("New task")); const form = screen.getByRole("form", { name: "New task" }); fireEvent.change(within(form).getByLabelText("What needs doing"), { target: { value: "x" } })
    expect((within(form).getByText("Add task") as HTMLButtonElement).disabled).toBe(true); expect(within(form).getByText("Choose a customer.")).toBeTruthy()
  })
  it("shows the server's refusal and keeps the list", async () => {
    patch.mockRejectedValue({ response: { data: { message: "The due date cannot be in the past" } } }); wrap(); fireEvent.change(await screen.findByLabelText("Snooze Acme Mining"), { target: { value: "tomorrow" } })
    expect((await screen.findByRole("alert")).textContent).toMatch(/past/); expect(screen.getByLabelText("Acme Mining: Call Acme Mining")).toBeTruthy()
  })
  it("lists finished tasks for 30 days and reopens one", async () => {
    get.mockImplementation((url: string, cfg?: { params?: { done?: boolean } }) => (url.includes("identity/users") ? ok({ content: users }) : cfg?.params?.done ? ok({ tasks: [task("z", "Zulu", -2, { completed: true, outcome: "COMPLETED", completedAt: "2026-10-19T08:00:00Z" })], truncated: false }) : ok({ tasks, truncated: false })))
    wrap(); fireEvent.click(await screen.findByRole("tab", { name: "Done (last 30 days)" })); const row = await screen.findByLabelText("Zulu: Call Zulu"); expect(row.textContent).toContain("Done · 2026-10-19")
    fireEvent.click(screen.getByLabelText("Reopen: Zulu")); await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customers/c-z/followups/z/reopen"))
  })
  it("read-only users see the queue without action buttons; empty, error and truncated states are explained", async () => {
    perms = new Set(["CUSTOMER_READ"]); wrap(); await screen.findByLabelText("Acme Mining: Call Acme Mining"); expect(screen.queryByLabelText("Done: Acme Mining")).toBeNull(); expect(screen.queryByText("New task")).toBeNull(); cleanup()
    get.mockImplementation(() => ok({ tasks: [], truncated: false })); wrap(); expect(await screen.findByText(/No open tasks/)).toBeTruthy(); cleanup()
    get.mockRejectedValue(new Error("x")); wrap(); expect((await screen.findByRole("alert")).textContent).toMatch(/Couldn't load/); cleanup()
    get.mockImplementation((url: string) => (url.includes("identity/users") ? Promise.reject(new Error("403")) : ok({ tasks, truncated: true }))); wrap(); expect((await screen.findByRole("status")).textContent).toMatch(/more tasks/)
  })
})
