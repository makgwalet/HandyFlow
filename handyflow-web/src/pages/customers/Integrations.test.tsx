import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import IntegrationsPanel from "./IntegrationsPanel"
import { EMPTY_WEBHOOK, captureExample, eventLabel, keyProblem, keyStatus, toggledEvent, urlProblem, webhookBody, webhookProblem, webhookStatus, type CaptureKey, type Webhook, type WebhookDelivery } from "./integrations.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); for (const m of [get, post, put, del]) m.mockReset(); for (const m of [post, put, del]) m.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

const key = (over: Partial<CaptureKey> = {}): CaptureKey => ({ id: "k1", name: "Website form", keyPrefix: "hf_crm_Ab12", defaultSource: "WEBSITE", createdAt: "2026-10-01T08:00:00Z", lastUsedAt: null, active: true, ...over })
const hook = (over: Partial<Webhook> = {}): Webhook => ({ id: "w1", name: "ERP sync", url: "https://erp.example.com/hook", events: ["LEAD_CREATED", "OPPORTUNITY_WON"], active: true, suspended: false, consecutiveFailures: 0, lastDeliveryAt: null, ...over })
const delivery = (over: Partial<WebhookDelivery> = {}): WebhookDelivery => ({ id: "d1", webhookId: "w1", webhookName: "ERP sync", event: "LEAD_CREATED", outcome: "DONE", statusCode: 200, detail: "Delivered (200)", sentAt: "2026-10-21T10:00:00Z", ...over })
const serve = (k: CaptureKey[], h: Webhook[], d: WebhookDelivery[]) => get.mockImplementation((url: string) => ok(url.includes("deliveries") ? d : url.includes("webhooks") ? h : k))

describe("integration logic", () => {
  it("checks key names and addresses", () => {
    expect(keyProblem(" ")).toBe("Name the key."); expect(keyProblem("x".repeat(121))).toBe("The name can be at most 120 characters."); expect(keyProblem("Form")).toBeNull()
    expect(urlProblem("")).toBe("Give the address to send to."); expect(urlProblem("not a url")).toBe("That address is not valid."); expect(urlProblem("http://a.example.com")).toBe("The address must start with https://.")
    expect(urlProblem("https://u:p@a.example.com")).toBe("The address cannot contain a user name or password."); expect(urlProblem("https://a.example.com/" + "p".repeat(500))).toBe("The address can be at most 500 characters.")
    for (const u of ["https://localhost/x", "https://db.internal/x", "https://printer.local", "https://intranet/x", "https://127.0.0.1/x", "https://10.1.2.3/x", "https://192.168.1.1/x", "https://172.20.0.1/x", "https://169.254.169.254/x", "https://100.64.0.1/x", "https://[::1]/x", "https://[fd00::1]/x"])
      expect(urlProblem(u), u).toBe("That address points inside a private network.")
    for (const u of ["https://hooks.example.com/a?b=1", "https://8.8.8.8/x", "https://172.32.0.1/x", "https://100.63.0.1/x"]) expect(urlProblem(u), u).toBeNull()
  })
  it("checks the webhook form and builds its body", () => {
    const f = { ...EMPTY_WEBHOOK, name: "ERP", url: "https://erp.example.com/h" }
    expect(webhookProblem(f)).toBeNull(); expect(webhookProblem({ ...f, name: "" })).toBe("Name the webhook."); expect(webhookProblem({ ...f, events: [] })).toBe("Choose at least one event."); expect(webhookProblem({ ...f, url: "http://x.example.com" })).toBe("The address must start with https://.")
    expect(webhookBody({ ...f, name: " ERP ", url: " https://erp.example.com/h " })).toEqual({ name: "ERP", url: "https://erp.example.com/h", events: ["LEAD_CREATED"], active: true })
    expect(toggledEvent(["LEAD_CREATED"], "STAGE_CHANGED")).toEqual(["LEAD_CREATED", "STAGE_CHANGED"]); expect(toggledEvent(["LEAD_CREATED", "STAGE_CHANGED"], "LEAD_CREATED")).toEqual(["STAGE_CHANGED"])
  })
  it("words status and events", () => {
    expect(webhookStatus(hook())).toBe("On"); expect(webhookStatus(hook({ active: false }))).toBe("Off"); expect(webhookStatus(hook({ consecutiveFailures: 1 }))).toBe("On, but the last 1 attempt failed")
    expect(webhookStatus(hook({ suspended: true, consecutiveFailures: 10 }))).toBe("Stopped after 10 failures in a row. Save it again to switch it back on.")
    expect(keyStatus(key())).toBe("Not used yet"); expect(keyStatus(key({ active: false }))).toBe("Revoked"); expect(keyStatus(key({ lastUsedAt: "2026-10-19T08:00:00Z" }))).toMatch(/^Last used /)
    expect(eventLabel("OPPORTUNITY_WON")).toBe("An opportunity is won"); expect(eventLabel("PING")).toBe("Test message")
    expect(captureExample("https://app.example.com")).toContain("https://app.example.com/api/v1/public/crm/leads")
  })
})

describe("IntegrationsPanel", () => {
  it("lists keys, webhooks and the log", async () => {
    serve([key(), key({ id: "k2", name: "Old form", active: false })], [hook(), hook({ id: "w2", name: "Stopped hook", suspended: true, consecutiveFailures: 10 })], [delivery(), delivery({ id: "d2", outcome: "FAILED", detail: "The receiver had an error (503)" })])
    wrap(<IntegrationsPanel />)
    const k = await screen.findByLabelText("Key Website form"); expect(within(k).getByText(/Not used yet/)).toBeTruthy(); expect(within(k).getByText("hf_crm_Ab12…")).toBeTruthy()
    expect(within(screen.getByLabelText("Key Old form")).getByText(/Revoked/)).toBeTruthy(); expect(screen.queryByLabelText("Revoke Old form")).toBeNull()
    const w = await screen.findByLabelText("Webhook ERP sync"); expect(within(w).getByText(/A lead is added, An opportunity is won · On/)).toBeTruthy()
    expect(within(screen.getByLabelText("Webhook Stopped hook")).getByText(/Stopped after 10 failures/)).toBeTruthy()
    expect(await screen.findByText(/Failed: The receiver had an error \(503\)/)).toBeTruthy()
  })
  it("makes a key and shows it once", async () => {
    serve([], [], [])
    post.mockResolvedValue({ data: { data: { key: key({ name: "Contact form" }), apiKey: "hf_crm_SECRETVALUE" } } })
    wrap(<IntegrationsPanel />)
    fireEvent.click(await screen.findByText("Add key"))
    const make = screen.getByText("Make key") as HTMLButtonElement; expect(make.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Key name"), { target: { value: "Contact form" } }); fireEvent.change(screen.getByLabelText("Default source"), { target: { value: "REFERRAL" } })
    fireEvent.click(make)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/integrations/keys", { name: "Contact form", defaultSource: "REFERRAL" }))
    const shown = await screen.findByLabelText("New secret"); expect(shown.textContent).toContain("hf_crm_SECRETVALUE"); expect(shown.textContent).toContain("shown only once")
    fireEvent.click(screen.getByText("I have saved it")); expect(screen.queryByLabelText("New secret")).toBeNull()
  })
  it("asks before revoking a key", async () => {
    serve([key()], [], [])
    wrap(<IntegrationsPanel />)
    fireEvent.click(await screen.findByLabelText("Revoke Website form")); expect(del).not.toHaveBeenCalled()
    fireEvent.click(screen.getByLabelText("Confirm revoke Website form")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/integrations/keys/k1"))
  })
  it("adds a webhook and shows its signing secret once", async () => {
    serve([], [], [])
    post.mockResolvedValue({ data: { data: { webhook: hook({ name: "CRM hook" }), secret: "whsec_TOPSECRET" } } })
    wrap(<IntegrationsPanel />)
    fireEvent.click(await screen.findByText("Add webhook"))
    const save = screen.getByText("Save webhook") as HTMLButtonElement; expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Webhook name"), { target: { value: "CRM hook" } })
    fireEvent.change(screen.getByLabelText("Webhook address"), { target: { value: "http://insecure.example.com" } })
    expect(screen.getByText("The address must start with https://.")).toBeTruthy(); expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Webhook address"), { target: { value: "https://crm.example.com/in" } })
    fireEvent.click(screen.getByLabelText("A lead changes stage")); expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/integrations/webhooks", { name: "CRM hook", url: "https://crm.example.com/in", events: ["LEAD_CREATED", "STAGE_CHANGED"], active: true }))
    expect((await screen.findByLabelText("New secret")).textContent).toContain("whsec_TOPSECRET")
  })
  it("tests, edits, switches off and deletes a webhook", async () => {
    serve([], [hook()], [])
    post.mockResolvedValue({ data: { data: delivery({ event: "PING", detail: "Delivered (200)" }) } })
    wrap(<IntegrationsPanel />)
    fireEvent.click(await screen.findByLabelText("Test ERP sync")); expect(await screen.findByText("Test worked: Delivered (200)")).toBeTruthy(); expect(post).toHaveBeenCalledWith("/api/v1/crm/integrations/webhooks/w1/test")
    fireEvent.click(screen.getByLabelText("Turn off ERP sync")); await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/integrations/webhooks/w1", expect.objectContaining({ active: false, events: ["LEAD_CREATED", "OPPORTUNITY_WON"] })))
    fireEvent.click(screen.getByLabelText("Edit ERP sync")); fireEvent.change(screen.getByLabelText("Webhook address"), { target: { value: "https://new.example.com/h" } }); fireEvent.click(screen.getByText("Save webhook"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/integrations/webhooks/w1", expect.objectContaining({ url: "https://new.example.com/h" })))
    fireEvent.click(screen.getByLabelText("Delete ERP sync")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/integrations/webhooks/w1"))
  })
  it("hides editing without permission and shows a server refusal", async () => {
    perms = new Set(["CUSTOMER_READ"]); serve([key()], [hook()], [])
    wrap(<IntegrationsPanel />)
    await screen.findByLabelText("Webhook ERP sync"); expect(screen.queryByText("Add key")).toBeNull(); expect(screen.queryByText("Add webhook")).toBeNull(); expect(screen.queryByLabelText("Test ERP sync")).toBeNull()
    cleanup(); perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); serve([], [hook()], [])
    post.mockRejectedValue({ response: { data: { message: "That address points inside a private network" } } })
    wrap(<IntegrationsPanel />)
    fireEvent.click(await screen.findByLabelText("Test ERP sync")); expect((await screen.findByRole("alert")).textContent).toContain("private network")
  })
})
