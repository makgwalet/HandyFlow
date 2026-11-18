// Fuel visual check: drives the real web app in Chromium against made-up API data and saves screenshots (light, dark, phone).
// NOT live data. Needs Playwright installed and a dev server: npx vite --port 5199 --host 127.0.0.1
// Usage: NODE_PATH=$(npm root -g) node scripts/fuel-visual-check.cjs [sections,comma,separated] [outDir]
// Fixtures are hand-written to match the API shapes; when a tab changes its API, update them here.
const { chromium } = require("playwright")
const BASE = "http://127.0.0.1:5199"
const iso = (h, d = 0) => { const t = new Date(); t.setDate(t.getDate() + d); t.setHours(h, 0, 0, 0); return t.toISOString() }
const ZA = "Africa/Johannesburg"

const tanks = [
  { id: "tk1", name: "Diesel 50 Tank A", fuelType: "DIESEL", capacityLitres: 80000, currentLitres: 52300, fillPercentage: 65.4, low: false, location: "Main depot", createdAt: iso(8, -200), lowThresholdPct: 20 },
  { id: "tk2", name: "Diesel 500 Tank B", fuelType: "DIESEL", capacityLitres: 60000, currentLitres: 9800, fillPercentage: 16.3, low: true, location: "Main depot", createdAt: iso(8, -200), lowThresholdPct: 20 },
  { id: "tk3", name: "Petrol 95 Forecourt", fuelType: "PETROL_95", capacityLitres: 30000, currentLitres: 21000, fillPercentage: 70, low: false, location: "Forecourt", createdAt: iso(8, -200), lowThresholdPct: 20 },
]
const deliveries = [
  { id: "d1", tankId: "tk1", customerId: "c1", deliveryAddress: { street: "Mine Rd 1", city: "Rustenburg" }, fuelType: "DIESEL", litresOrdered: 10000, litresDelivered: 9950, pricePerLitre: 21.5, totalAmount: 213925, status: "DELIVERED", scheduledAt: iso(8, -1), deliveredAt: iso(11, -1), driverName: "Thabo Mokoena", vehicleReg: "AB 12 CD GP", receiverName: "Sipho Dlamini", receiverIdBadge: "B-114", receiptNumber: "RC-000412", signedOnBehalf: false, onBehalfOf: null, createdAt: iso(5, -2) },
  { id: "d2", tankId: "tk1", customerId: "c2", deliveryAddress: { street: "Farm 7", city: "Brits" }, fuelType: "DIESEL", litresOrdered: 6000, litresDelivered: null, pricePerLitre: 21.5, totalAmount: 129000, status: "IN_TRANSIT", scheduledAt: iso(9, 0), deliveredAt: null, driverName: "Thabo Mokoena", vehicleReg: "AB 12 CD GP", receiverName: null, receiverIdBadge: null, receiptNumber: null, signedOnBehalf: false, onBehalfOf: null, createdAt: iso(5, -1) },
  { id: "d3", tankId: "tk2", customerId: "c1", deliveryAddress: null, fuelType: "DIESEL", litresOrdered: 8000, litresDelivered: null, pricePerLitre: 21.5, totalAmount: 172000, status: "SCHEDULED", scheduledAt: iso(14, 0), deliveredAt: null, driverName: null, vehicleReg: null, receiverName: null, receiverIdBadge: null, receiptNumber: null, signedOnBehalf: false, onBehalfOf: null, createdAt: iso(5, -1) },
]
const dispatches = [
  { id: "x1", tankId: "tk1", litresDispensed: 450, pricePerLitre: null, recipientName: "Workshop truck", authorisedBy: "J. Botha", dispatchedAt: iso(7, -1), levelBefore: 52750, levelAfter: 52300, odometerReading: 123400, hoursReading: null, reversedAt: null, reversalReason: null, transferId: null },
  { id: "x2", tankId: "tk3", litresDispensed: 2000, pricePerLitre: 22.1, recipientName: "Khumalo Transport", authorisedBy: "J. Botha", dispatchedAt: iso(10, -2), levelBefore: 23000, levelAfter: 21000, odometerReading: null, hoursReading: null, reversedAt: null, reversalReason: null, transferId: null },
]
const accounts = [
  { id: "a1", crmCustomerId: "c1", customerName: "Gold Mine Ltd", customerType: "MINING_COMPANY", creditLimit: 500000, paymentTermsDays: 30, creditHold: false, creditHoldReason: null, licenceNumber: "LIC-1", notes: null, siteCount: 2, sites: [] },
  { id: "a2", crmCustomerId: "c2", customerName: "Brits Farms (Pty) Ltd", customerType: "COMMERCIAL_FARM", creditLimit: 150000, paymentTermsDays: 30, creditHold: true, creditHoldReason: "Overdue 60 days", licenceNumber: null, notes: null, siteCount: 1, sites: [] },
]
const orders = [
  { id: "o1", orderNumber: "SO-0101", accountId: "a1", customerId: "c1", customerName: "Gold Mine Ltd", siteId: null, siteName: "Shaft 4", fuelType: "DIESEL", litresOrdered: 20000, pricePerLitre: 21.5, value: 430000, status: "CONFIRMED", requestedDate: iso(8, 2).slice(0, 10), quoteValidUntil: null, tankId: "tk1", tankName: "Diesel 50 Tank A", litresDelivered: 9950, litresScheduled: 8000, litresRemaining: 2050, creditOverrideReason: null, cancelReason: null, customerReference: "PO-7781", notes: null, deliveries: [] },
  { id: "o2", orderNumber: "SO-0102", accountId: "a2", customerId: "c2", customerName: "Brits Farms (Pty) Ltd", siteId: null, siteName: null, fuelType: "DIESEL", litresOrdered: 6000, pricePerLitre: 21.5, value: 129000, status: "QUOTE", requestedDate: null, quoteValidUntil: iso(8, 5).slice(0, 10), tankId: null, tankName: null, litresDelivered: 0, litresScheduled: 0, litresRemaining: 6000, creditOverrideReason: null, cancelReason: null, customerReference: null, notes: null, deliveries: [] },
]
const queue = [{ deliveryId: "d1", receiptNumber: "RC-000412", customerId: "c1", customerName: "Gold Mine Ltd", fuelType: "DIESEL", litresDelivered: 9950, pricePerLitre: 21.5, totalAmount: 213925, deliveredAt: iso(11, -1), invoiceState: "NOT_INVOICED", invoiceError: null }]
const tankerList = [
  { id: "t1", registration: "AB 12 CD GP", name: "Big Blue", active: true, totalCapacity: 20000, compartments: [{ id: "c1", number: 1, capacityLitres: 10000 }, { id: "c2", number: 2, capacityLitres: 6000 }, { id: "c3", number: 3, capacityLitres: 4000 }], openLoads: 2, fleetVehicleId: "v1", fleetVehicleLabel: "AB 12 CD GP" },
  { id: "t2", registration: "EF 34 GH GP", name: null, active: true, totalCapacity: 12000, compartments: [{ id: "c4", number: 1, capacityLitres: 12000 }], openLoads: 0, fleetVehicleId: null },
]
const planLoads = [
  { deliveryId: "d2", status: "IN_TRANSIT", scheduledAt: iso(9, 0), customerId: "c2", customerName: "Brits Farms (Pty) Ltd", fuelType: "DIESEL", litres: 6000, tankerId: "t1", tankerRegistration: "AB 12 CD GP", driverEmployeeId: "e1", driverName: "Thabo Mokoena", allocated: true, failedAttempts: 0 },
  { deliveryId: "d3", status: "SCHEDULED", scheduledAt: iso(14, 0), customerId: "c1", customerName: "Gold Mine Ltd", fuelType: "DIESEL", litres: 8000, tankerId: "t1", tankerRegistration: "AB 12 CD GP", driverEmployeeId: null, driverName: null, allocated: true, failedAttempts: 0 },
]
const divisionRow = (division, label, count, litres, revenue, prevR) => ({ division, label, count, litres, revenue, dailyRevenue: Array.from({ length: 30 }, (_, i) => Math.round((revenue / 30) * (0.6 + 0.8 * Math.abs(Math.sin(i))))), previousCount: count - 1, previousLitres: litres * 0.9, previousRevenue: prevR })
const live = [{ vehicleId: "v1", registration: "AB 12 CD GP", make: "Volvo", model: "FM", latitude: -25.67, longitude: 27.24, speedKph: 74, heading: 90, ignitionOn: true, recordedAt: iso(9), ageMinutes: 3, status: "MOVING" }]
const variances = { toleranceSet: true, variances: [
  { id: "v1", deliveryId: "d1", receiptNumber: "RC-000412", driverName: "Thabo Mokoena", vehicleReg: "AB 12 CD GP", loadedLitres: 10000, deliveredLitres: 9950, varianceLitres: -50, variancePct: 0.5, tolerancePct: 0.3, status: "OPEN", detectedAt: iso(12, -1) },
  { id: "v2", deliveryId: "d2", receiptNumber: "RC-000398", driverName: "Thabo Mokoena", vehicleReg: "AB 12 CD GP", loadedLitres: 8000, deliveredLitres: 7880, varianceLitres: -120, variancePct: 1.5, tolerancePct: 0.3, status: "INVESTIGATING", detectedAt: iso(12, -3) },
  { id: "v3", deliveryId: "d3", receiptNumber: "RC-000377", driverName: "Lerato Nkosi", vehicleReg: "EF 34 GH GP", loadedLitres: 6000, deliveredLitres: 6030, varianceLitres: 30, variancePct: 0.5, tolerancePct: 0.3, status: "CLOSED", decision: "EXPLAINED", note: "Temperature", detectedAt: iso(12, -9) } ] }
const execution = { deliveryId: "d1", status: "DELIVERED", driverName: "Thabo Mokoena", vehicleReg: "AB 12 CD GP", receiverName: "Sipho Dlamini", signedOnBehalf: false, litresOrdered: 10000, litresLoaded: 10000, litresDelivered: 9950, meterStart: 100, meterEnd: 10050, failedAttempts: 1, events: [
  { at: iso(5, -2), type: "SCHEDULED", text: "Load created", severity: "OK" }, { at: iso(7, -1), type: "LOADED", text: "Loaded 10000 L, seals A1, A2", severity: "OK" },
  { at: iso(9, -1), type: "FAILED_ATTEMPT", text: "Attempt 1 failed: Site closed (gate locked)", severity: "PROBLEM" },
  { at: iso(10, -1), type: "ARRIVAL_CHECK", text: "Arrival check passed", severity: "OK" }, { at: iso(11, -1), type: "EVIDENCE", text: "Signature captured at -25.67000, 27.24000", severity: "OK" },
  { at: iso(11, -1), type: "DELIVERED", text: "Delivered 9950 L, received by Sipho Dlamini", severity: "OK" }] }

let CURRENT_THEME = "light"
function route(url, method) {
  const u = new URL(url); const p = u.pathname; const q = u.searchParams
  const ok = d => ({ success: true, data: d })
  if (p === "/api/v1/identity/ui-preferences") return ok({ effective: {}, tenant: { brandColor: "NAVY", brandLocked: false, defaultTheme: CURRENT_THEME === "dark" ? "DARK" : "LIGHT", defaultSidebar: "FULL", defaultContainer: "BOXED" }, user: { theme: null, sidebar: null, container: null, brandColor: null, pinnedModules: [] }, canEditTenant: false })
  if (method !== "GET") return ok({})
  if (p === "/api/v1/billing/modules/mine") return ["fuel","fleet","crm"].map(k => ({ moduleKey: k, accessible: true }))
  if (p === "/api/v1/fuel/tanks") return ok(tanks)
  if (p === "/api/v1/fuel/deliveries") return ok({ content: deliveries })
  if (p === "/api/v1/fuel/dispatches") return ok(dispatches)
  if (p === "/api/v1/fuel/customer-accounts") return ok(accounts)
  if (p === "/api/v1/fuel/orders") return ok(orders)
  if (p === "/api/v1/fuel/invoicing/queue") return ok(queue)
  if (p === "/api/v1/fuel/tankers") return ok(tankerList)
  if (p === "/api/v1/fuel/planning") return ok({ date: q.get("date"), loads: planLoads, tankers: [] })
  if (p === "/api/v1/fleet/telematics/live") return ok(live)
  if (p === "/api/v1/fuel/tankers/t1/compartment-ratings") return ok([{ compartmentId: "c1", number: 1, capacityLitres: 10000, fuelTypes: ["DIESEL"], lastCargo: "DIESEL", lastCargoAt: iso(9, -1), lastCleanedAt: iso(9, -20) }, { compartmentId: "c2", number: 2, capacityLitres: 6000, fuelTypes: ["DIESEL", "PETROL_95"], lastCargo: "PETROL_95", lastCargoAt: iso(9, -3), lastCleanedAt: null }, { compartmentId: "c3", number: 3, capacityLitres: 4000, fuelTypes: [], lastCargo: null, lastCargoAt: null, lastCleanedAt: null }])
  if (p === "/api/v1/fuel/tankers/t1/compartment-loads") return ok([{ deliveryId: "d2", status: "IN_TRANSIT", scheduledAt: iso(9, 0), customerName: "Brits Farms (Pty) Ltd", fuelType: "DIESEL", compartmentId: "c1", litres: 6000 }, { deliveryId: "d3", status: "SCHEDULED", scheduledAt: iso(14, 0), customerName: "Gold Mine Ltd", fuelType: "DIESEL", compartmentId: "c1", litres: 5000 }, { deliveryId: "d3", status: "SCHEDULED", scheduledAt: iso(14, 0), customerName: "Gold Mine Ltd", fuelType: "DIESEL", compartmentId: "c2", litres: 3000 }])
  if (p === "/api/v1/fuel/division-sales") return ok({ from: "2026-10-20", to: "2026-11-18", previousFrom: "2026-09-20", previousTo: "2026-10-19", moneyShown: true,
    divisions: [divisionRow("DISTRIBUTION", "Distribution", 41, 312000, 6700000, 5900000), divisionRow("RETAIL_FORECOURT", "Retail forecourt", 58, 184000, 4100000, 4300000), divisionRow("WHOLESALE_SUPPLY", "Wholesale supply", 12, 90000, 1900000, 1500000), { ...divisionRow("TRUCK_STOP", "Truck stop", 33, 0, 98000, 0), litres: 0, previousLitres: 0 }],
    total: divisionRow("TOTAL", "All divisions", 144, 586000, 12798000, 11700000) })
  if (p === "/api/v1/fuel/variances") return ok(variances)
  if (/\/deliveries\/[^/]+\/execution$/.test(p)) return ok(execution)
  if (p === "/api/v1/fuel/dashboard-summary") return ok({ today: iso(0).slice(0, 10), monthLitres: 420000, monthDispatches: 48, monthReceipts: 5, dailyLitres: [12000, 18000, 15000, 22000, 9000, 17000, 20000, 14000, 19000, 16000], negativeVarianceReadings: 1, openDeliveries: 3, inTransitDeliveries: 1, overdueDeliveries: 0, nextDeliveries: [{ id: "d2", status: "IN_TRANSIT", fuelType: "DIESEL", litresOrdered: 6000, scheduledAt: iso(9) }] })
  if (p === "/api/v1/fuel/forecourt/summary") return ok({ days: 7, shifts: 14, litres: 41200, sales: 905000, variance: -420, flaggedShifts: 1 })
  if (p === "/api/v1/fuel/truck-stop/stays/occupancy") return ok({ inYard: 12, arrivals: 7, departures: 5, bays: 20, free: 8, overstays: 1 })
  if (p === "/api/v1/fuel/truck-stop/stays/capacity") return ok({ bays: 20 })
  if (p === "/api/v1/fuel/operating-profile") return ok({ status: "ACTIVE", activeDivisions: ["WHOLESALE_SUPPLY", "DISTRIBUTION", "RETAIL_FORECOURT", "TRUCK_STOP"] })
  if (p === "/api/v1/crm/customers") return ok({ content: [] })
  return ok([])
}

const sections = process.argv[2] ? process.argv[2].split(",") : ["dashboard", "tanks", "dispatches", "deliveries", "orders", "planning", "customers", "invoicing", "division-sales", "fleet-map", "compartments", "variances"]
;(async () => {
  const browser = await chromium.launch()
  const outDir = process.argv[3] || "."
  const unmatched = new Set(); const errors = []
  for (const [theme, w, h, name] of [["light", 1280, 900, "desktop-light"], ["dark", 1280, 900, "desktop-dark"], ["light", 390, 844, "phone-light"]]) {
    CURRENT_THEME = theme
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, colorScheme: theme, deviceScaleFactor: 1, timezoneId: ZA, locale: "en-ZA" })
    await ctx.addInitScript(([th]) => {
      localStorage.setItem("handyflow-auth", JSON.stringify({ state: { token: "t", isAuthenticated: true, user: { userId: "u1", tenantId: "t1", email: "mgr@example.com", firstName: "Anele", lastName: "Mgr", permissions: ["FUEL_READ", "FUEL_MANAGE", "FUEL_MARGIN_READ", "FUEL_VARIANCE_REVIEW", "FUEL_TRANSPORT_MANAGE", "FUEL_DISTRIBUTION_MANAGE", "FLEET_READ", "CRM_READ"] } }, version: 0 }))
      try { localStorage.setItem("theme", th) } catch {}
    }, [theme])
    const page = await ctx.newPage()
    page.on("pageerror", e => errors.push(`${name}: ${e.message}`))
    page.on("console", m => { if (m.type() === "error") errors.push(`${name}: ${m.text().slice(0, 200)}`) })
    await page.route("http://localhost:8080/**", r => {
      const req = r.request(); const u = new URL(req.url())
      const body = route(req.url(), req.method())
      if (body && Array.isArray(body.data) && body.data.length === 0 && !/(live|tankers|queue|compartment|variances)/.test(u.pathname)) unmatched.add(u.pathname)
      const cors = { "access-control-allow-origin": BASE, "access-control-allow-credentials": "true", "access-control-allow-headers": "*", "access-control-allow-methods": "*" }
      if (req.method() === "OPTIONS") return r.fulfill({ status: 204, headers: cors })
      r.fulfill({ status: 200, contentType: "application/json", headers: cors, body: JSON.stringify(body) })
    })
    for (const s of sections) {
      await page.goto(`${BASE}/fuel/${s}`, { waitUntil: "networkidle" }).catch(() => {})
      await page.waitForTimeout(700)
      if (name === "desktop-light" && s === "deliveries") { const b = page.getByRole("button", { name: /Timeline for/ }).first(); if (await b.count()) { await b.click(); await page.waitForTimeout(500); await page.screenshot({ path: `${outDir}/${name}-delivery-timeline.png` }); await page.keyboard.press("Escape"); } }
      await page.screenshot({ path: `${outDir}/${name}-${s}.png`, fullPage: true })
    }
    await ctx.close()
  }
  await browser.close()
  console.log("UNMATCHED (empty fixture):", [...unmatched].join("\n  "))
  console.log("ERRORS:", [...new Set(errors)].slice(0, 25).join("\n  "))
})()
