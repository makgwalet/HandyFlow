import { ordersFor, usableSuppliers } from "@depot/supply";
import { canReceiveFuel, canRecordDip, dipBody, emptyReceive, fillText, receiveBody, sortTanks, tankSchema, validateDip, validateReceive, varianceText, type ReceiveForm, type Tank } from "@depot/tanks";

const tank = (o: Partial<Tank> = {}): Tank => ({ id: "t", name: "Tank 1", fuelType: "DIESEL_50", capacityLitres: 30000, currentLitres: 12000, low: false, ...o });

describe("tanks", () => {
  it("accepts the server shape and ignores extra fields", () => {
    expect(tankSchema.parse({ id: "a", name: "T", fuelType: "ULP95", capacityLitres: 1, currentLitres: 1, createdAt: "x" }).low).toBe(false);
  });
  it("shows litres and percentage", () => expect(fillText(tank())).toMatch(/12.000 L of 30.000 L \(40%\)/));
  it("lists low tanks first, then by name", () => {
    expect(sortTanks([tank({ id: "b", name: "B" }), tank({ id: "a", name: "A" }), tank({ id: "c", name: "Z", low: true })]).map((t) => t.id)).toEqual(["c", "a", "b"]);
  });
  it("lets only managers record a dip", () => {
    expect(canRecordDip(["FUEL_OPERATIONS_MANAGE"])).toBe(true);
    expect(canRecordDip(["FUEL_MANAGE"])).toBe(true);
    expect(canRecordDip(["FUEL_READ", "FUEL_STOCK_MANAGE"])).toBe(false);
  });
});

describe("dip reading", () => {
  it("needs a number", () => {
    expect(validateDip("")).toMatch(/enter/i);
    expect(validateDip("abc")).toMatch(/number/i);
    expect(validateDip("-5")).toMatch(/zero or more/i);
  });
  it("accepts zero and a comma decimal", () => {
    expect(validateDip("0", 100)).toBeNull();
    expect(validateDip("12,5", 100)).toBeNull();
  });
  it("cannot be above the tank's capacity", () => expect(validateDip("30001", 30000)).toMatch(/capacity/));
  it("builds the request body", () => expect(dipBody("12,5", "2026-01-01T00:00:00.000Z", "Sipho D")).toEqual({ actualLitres: 12.5, readAt: "2026-01-01T00:00:00.000Z", readBy: "Sipho D" }));
  it("describes variance", () => {
    expect(varianceText({ varianceLitres: -120.4, negativeVariance: true })).toBe("Short by 120 L");
    expect(varianceText({ varianceLitres: 50, negativeVariance: false })).toBe("Over by 50 L");
    expect(varianceText({ varianceLitres: 0.2, negativeVariance: false })).toBe("Matches the book stock");
    expect(varianceText({ varianceLitres: null, negativeVariance: false })).toBe("No variance calculated");
  });
  const tank = { capacityLitres: 30000, currentLitres: 12000 };
  const f = (o: Partial<ReceiveForm> = {}): ReceiveForm => ({ ...emptyReceive, litres: "10000", price: "21.50", ...o });
  it("accepts a receipt that fits", () => expect(validateReceive(f(), tank)).toBeNull());
  it("asks for litres and price", () => {
    expect(validateReceive(f({ litres: "" }), tank)).toMatch(/litres received/i);
    expect(validateReceive(f({ price: "" }), tank)).toMatch(/price/i);
    expect(validateReceive(f({ litres: "0" }), tank)).toMatch(/more than zero/);
    expect(validateReceive(f({ price: "-1" }), tank)).toMatch(/more than zero/);
  });
  it("refuses more fuel than the room left, and says how much room there is", () => {
    expect(validateReceive(f({ litres: "18000" }), tank)).toBeNull();
    expect(validateReceive(f({ litres: "18001" }), tank)).toMatch(/18.000 L of room/);
  });
  it("checks freight only when given", () => {
    expect(validateReceive(f({ freight: "" }), tank)).toBeNull();
    expect(validateReceive(f({ freight: "x" }), tank)).toMatch(/Freight/);
  });
  it("builds the receipt body with only what was filled in", () => {
    expect(receiveBody(f({ litres: "10 000".replace(" ", ""), price: "21,5" }), "2026-01-01T00:00:00.000Z")).toEqual({ litresReceived: 10000, pricePerLitre: 21.5, receivedAt: "2026-01-01T00:00:00.000Z" });
    expect(receiveBody(f({ deliveryNote: " DN1 ", invoiceRef: "INV9", freight: "350" }), "t")).toMatchObject({ deliveryNote: "DN1", invoiceRef: "INV9", freightAmount: 350 });
  });
  it("lets the same people receive as record dips", () => {
    expect(canReceiveFuel(["FUEL_OPERATIONS_MANAGE"])).toBe(true);
    expect(canReceiveFuel(["FUEL_READ"])).toBe(false);
  });
  it("needs a supplier before an order", () => expect(validateReceive(f({ purchaseOrderId: "P1" }), tank)).toMatch(/supplier/i));
  it("sends the supplier and order when chosen", () => expect(receiveBody(f({ supplierId: "S1", purchaseOrderId: "P1" }), "t")).toMatchObject({ supplierId: "S1", purchaseOrderId: "P1" }));
  it("offers only approved suppliers, by name", () => {
    const l = usableSuppliers([{ id: "b", name: "Zed", approvalStatus: "APPROVED" }, { id: "c", name: "Mid", approvalStatus: "SUSPENDED" }, { id: "a", name: "Alpha" }]);
    expect(l.map((s) => s.id)).toEqual(["a", "b"]);
  });
  it("offers only placed orders for this supplier and this tank's fuel", () => {
    const o = (id: string, supplierId: string, fuelType: string, status: string) => ({ id, poNumber: id, supplierId, fuelType, status });
    const list = [o("ok", "S1", "DIESEL_50", "ORDERED"), o("part", "S1", "diesel_50", "PART_RECEIVED"), o("draft", "S1", "DIESEL_50", "DRAFT"), o("other", "S2", "DIESEL_50", "ORDERED"), o("ulp", "S1", "ULP95", "ORDERED"), o("done", "S1", "DIESEL_50", "RECEIVED")];
    expect(ordersFor(list, "S1", "DIESEL_50").map((x) => x.id)).toEqual(["ok", "part"]);
    expect(ordersFor(list, null, "DIESEL_50")).toEqual([]);
  });
});
