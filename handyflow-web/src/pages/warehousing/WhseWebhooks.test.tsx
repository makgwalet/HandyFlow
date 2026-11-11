import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseWebhooksTab, { type Webhook, type Delivery } from "./WhseWebhooksTab"
import { canRedeliver, chosenEvents, eventLabel, urlProblem } from "./webhooks"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const EVENTS = ["inbound.received", "outbound.picking_started", "outbound.shipped", "outbound.cancelled"]
const hook = (over: Partial<Webhook> = {}): Webhook => ({ id: "W1", name: "ERP", url: "https://erp.example.com/h", events: ["*"], active: true, suspended: false, consecutiveFailures: 0, createdAt: "2026-11-10T08:00:00Z", ...over })
const delivery = (over: Partial<Delivery> = {}): Delivery => ({ id: "D1", webhookId: "W1", webhookName: "ERP", eventType: "outbound.shipped", eventId: "E1", status: "DEAD", attempts: 6, nextAttemptAt: "2026-11-10T09:00:00Z",
  statusCode: 500, detail: "The receiver had an error (500)", lastAttemptAt: null, deliveredAt: null, createdAt: "2026-11-10T08:00:00Z", ...over })

function serve(hooks: Webhook[], deliveries: Delivery[] = []) {
  get.mockImplementation(async (url: string) => {
    if (url.endsWith("/webhooks/events")) return { data: EVENTS }
    if (url.endsWith("/webhooks/deliveries")) return { data: deliveries }
    if (url.endsWith("/webhooks")) return { data: hooks }
    return { data: [] }
  })
}

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe("webhook helpers", () => {
  it("accepts a public https address and refuses the rest", () => {
    expect(urlProblem("https://erp.example.com/hook")).toBeNull()
    expect(urlProblem("")).toMatch(/Give the address/)
    expect(urlProblem("http://erp.example.com")).toMatch(/https/)
    expect(urlProblem("https://u:p@erp.example.com")).toMatch(/user name/)
    expect(urlProblem("not a url")).toMatch(/not valid/)
    for (const u of ["https://localhost/x", "https://10.0.0.5/x", "https://192.168.1.2/x", "https://172.20.1.1/x", "https://169.254.169.254/x", "https://printer.local/x", "https://intranet/x"]) {
      expect(urlProblem(u), u).toMatch(/private network/)
    }
    expect(urlProblem("https://172.32.0.1/x")).toBeNull()
  })
  it("events: all ticked becomes a star, else the chosen ones in order", () => {
    expect(chosenEvents(true, [], EVENTS)).toEqual(["*"])
    expect(chosenEvents(false, EVENTS, EVENTS)).toEqual(["*"])
    expect(chosenEvents(false, ["outbound.shipped", "inbound.received"], EVENTS)).toEqual(["inbound.received", "outbound.shipped"])
    expect(chosenEvents(false, [], EVENTS)).toEqual([])
    expect(eventLabel("*")).toBe("All events")
    expect(eventLabel("outbound.shipped")).toBe("Order shipped")
  })
  it("only failed or retrying messages can be sent again", () => {
    expect(canRedeliver("DEAD")).toBe(true)
    expect(canRedeliver("RETRY")).toBe(true)
    expect(canRedeliver("DELIVERED")).toBe(false)
    expect(canRedeliver("PENDING")).toBe(false)
  })
})

describe("WhseWebhooksTab", () => {
  it("lists webhooks with their state and recent messages", async () => {
    serve([hook(), hook({ id: "W2", name: "Old", suspended: true, consecutiveFailures: 20 })], [delivery()])
    wrap(<WhseWebhooksTab />)
    expect(await screen.findByText("ERP", { selector: "strong" })).toBeTruthy()
    expect(screen.getByText(/Paused after repeated failures/)).toBeTruthy()
    expect(screen.getByText("Failed", { selector: "span" })).toBeTruthy()
    expect(screen.getByText("Send again")).toBeTruthy()
  })

  it("creating a webhook needs a good address, and the secret is shown once with the result", async () => {
    serve([])
    post.mockResolvedValue({ data: { webhook: hook(), secret: "whsec_abc123" } })
    wrap(<WhseWebhooksTab />)
    await screen.findByText("No webhooks yet.")
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "ERP" } })
    fireEvent.change(screen.getByLabelText("Address (https)"), { target: { value: "http://erp.example.com" } })
    expect((screen.getByText("Add webhook") as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/must start with https/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Address (https)"), { target: { value: "https://erp.example.com/h" } })
    fireEvent.click(screen.getByText("Add webhook"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/webhooks", { name: "ERP", url: "https://erp.example.com/h", events: ["*"] }))
    expect((await screen.findByTestId("wh-secret")).textContent).toBe("whsec_abc123")
    fireEvent.click(screen.getByText("I have copied it"))
    await waitFor(() => expect(screen.queryByTestId("wh-secret")).toBeNull())
  })

  it("choosing particular events sends just those", async () => {
    serve([])
    post.mockResolvedValue({ data: { webhook: hook(), secret: "s" } })
    wrap(<WhseWebhooksTab />)
    await screen.findByText("No webhooks yet.")
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Ship" } })
    fireEvent.change(screen.getByLabelText("Address (https)"), { target: { value: "https://erp.example.com/h" } })
    fireEvent.click(screen.getByLabelText("All events"))
    fireEvent.click(await screen.findByLabelText("Order shipped"))
    fireEvent.click(screen.getByText("Add webhook"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/webhooks", { name: "Ship", url: "https://erp.example.com/h", events: ["outbound.shipped"] }))
  })

  it("a test reports how it went", async () => {
    serve([hook()])
    post.mockResolvedValue({ data: { ok: false, statusCode: 404, detail: "The address was not found (404)" } })
    wrap(<WhseWebhooksTab />)
    fireEvent.click(await screen.findByText("Send a test"))
    expect(await screen.findByText(/Test failed\./)).toBeTruthy()
    expect(screen.getByText(/not found \(404\)/)).toBeTruthy()
    expect(post).toHaveBeenCalledWith("/api/v1/warehousing/webhooks/W1/test")
  })

  it("a paused webhook can be resumed and a failed message sent again", async () => {
    serve([hook({ suspended: true })], [delivery()])
    wrap(<WhseWebhooksTab />)
    fireEvent.click(await screen.findByText("Resume"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/webhooks/W1/resume"))
    fireEvent.click(screen.getByText("Send again"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/webhooks/deliveries/D1/redeliver"))
  })

  it("replacing the secret asks first and then shows the new one", async () => {
    serve([hook()])
    vi.spyOn(window, "confirm").mockReturnValue(true)
    post.mockResolvedValue({ data: { webhook: hook(), secret: "whsec_new" } })
    wrap(<WhseWebhooksTab />)
    fireEvent.click(await screen.findByText("Replace secret"))
    expect((await screen.findByTestId("wh-secret")).textContent).toBe("whsec_new")
  })

  it("deleting asks first; declining does nothing", async () => {
    serve([hook()])
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false)
    wrap(<WhseWebhooksTab />)
    fireEvent.click(await screen.findByText("Delete"))
    expect(confirm).toHaveBeenCalled()
    expect(del).not.toHaveBeenCalled()
    confirm.mockReturnValue(true)
    fireEvent.click(screen.getByText("Delete"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/warehousing/webhooks/W1"))
  })

  it("filtering by status asks the server for just those", async () => {
    serve([hook()])
    wrap(<WhseWebhooksTab />)
    await screen.findByText("ERP", { selector: "strong" })
    fireEvent.change(screen.getByLabelText("Filter by status"), { target: { value: "DEAD" } })
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/warehousing/webhooks/deliveries", { params: { status: "DEAD", limit: 50 } }))
  })
})
