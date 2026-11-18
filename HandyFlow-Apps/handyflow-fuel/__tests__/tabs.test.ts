import { bookBody, canCancelStay, quantityOf, rands, tabActions, tabSchema, validateBook, validateCancel, validateOpenTab, validateQuantity, validateVoid } from "@truckstop/tabs";

const tab = (o: any = {}) => tabSchema.parse({ id: "T1", vehicleRegistration: "ABC 123 GP", status: "OPEN", lines: [], total: 0, ...o });
const line = { id: "L1", description: "Shower", quantity: 1, unitPrice: "35.00", amount: "35.00" };

describe("tab actions", () => {
  it("an empty open tab can take lines or be voided, not settled", () => expect(tabActions(tab())).toEqual(["addLine", "void"]));
  it("a tab with lines settles at the till, or is charged when it has an account", () => {
    expect(tabActions(tab({ lines: [line] }))).toEqual(["addLine", "settle", "void"]);
    expect(tabActions(tab({ lines: [line], accountId: "A1" }))).toEqual(["addLine", "charge", "void"]);
  });
  it("a settled or voided tab offers nothing", () => {
    expect(tabActions(tab({ status: "SETTLED", lines: [line] }))).toEqual([]);
    expect(tabActions(tab({ status: "VOID" }))).toEqual([]);
  });
  it("reads amounts sent as text", () => expect(tab({ lines: [line] }).lines[0]?.amount).toBe(35));
});

describe("tab validation", () => {
  it("quantity", () => {
    expect(validateQuantity("")).toMatch(/above zero/); expect(validateQuantity("0")).toMatch(/above zero/); expect(validateQuantity("-1")).toMatch(/above zero/);
    expect(validateQuantity("abc")).toMatch(/above zero/); expect(validateQuantity("5000")).toMatch(/looks wrong/);
    expect(validateQuantity("2,5")).toBeNull(); expect(quantityOf("2,5")).toBe(2.5);
  });
  it("registration", () => { expect(validateOpenTab(" ")).toMatch(/registration/); expect(validateOpenTab("x".repeat(21))).toMatch(/too long/); expect(validateOpenTab("ABC 1 GP")).toBeNull(); });
  it("void reason is 5 to 300 characters", () => { expect(validateVoid("no")).toMatch(/5/); expect(validateVoid("x".repeat(301))).toMatch(/300/); expect(validateVoid("Wrong truck")).toBeNull(); });
  it("formats rands", () => expect(rands(1234.5)).toMatch(/^R\s?1\D?234[.,]50$/));
});

describe("desk booking", () => {
  const f = { vehicleRegistration: " abc 1 gp ", driverName: "", driverPhone: "", arrivalDate: "2026-11-20", departureDate: "2026-11-22", note: "" };
  it("accepts a good booking and tidies the body", () => {
    expect(validateBook(f, "2026-11-18")).toBeNull();
    expect(bookBody(f)).toEqual({ vehicleRegistration: "ABC 1 GP", driverName: null, driverPhone: null, arrivalDate: "2026-11-20", departureDate: "2026-11-22", note: null });
  });
  it("refuses a past arrival, a departure not after arrival, bad dates and a bad phone", () => {
    expect(validateBook({ ...f, arrivalDate: "2026-11-17" }, "2026-11-18")).toMatch(/past/);
    expect(validateBook({ ...f, departureDate: "2026-11-20" }, "2026-11-18")).toMatch(/after/);
    expect(validateBook({ ...f, departureDate: "2026-02-30" }, "2026-11-18")).toMatch(/yyyy-mm-dd/);
    expect(validateBook({ ...f, vehicleRegistration: "" }, "2026-11-18")).toMatch(/registration/);
    expect(validateBook({ ...f, driverPhone: "call me" }, "2026-11-18")).toMatch(/phone/);
  });
  it("only a stay not yet arrived can be cancelled, and a reason is required", () => {
    expect(canCancelStay("BOOKED")).toBe(true);
    expect(canCancelStay("CHECKED_IN")).toBe(false); expect(canCancelStay("CHECKED_OUT")).toBe(false);
    expect(validateCancel(" ")).toMatch(/reason/); expect(validateCancel("x".repeat(301))).toMatch(/300/); expect(validateCancel("Driver rang")).toBeNull();
  });
});
