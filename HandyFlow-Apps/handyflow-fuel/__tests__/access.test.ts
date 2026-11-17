import { accessSchema, emergencyContactOf, telUrl, type Access } from "@/api/access";

const base: Access = { legacyMode: false, activeDivisions: [], scope: { restricted: false, divisions: [], depotIds: [] }, emergencyContact: {} };

describe("access", () => {
  it("accepts the server shape", () => {
    expect(accessSchema.safeParse({ legacyMode: true, activeDivisions: [], scope: { restricted: false, divisions: [], depotIds: [] }, emergencyContact: { name: null, phone: null } }).success).toBe(true);
  });
  it("rejects an answer with no scope", () => {
    expect(accessSchema.safeParse({ legacyMode: true, activeDivisions: [], emergencyContact: {} }).success).toBe(false);
  });
  it("offers the emergency contact only when both name and number exist", () => {
    expect(emergencyContactOf({ ...base, emergencyContact: { name: "Control room", phone: "+27115550100" } })).toEqual({ name: "Control room", phone: "+27115550100" });
    expect(emergencyContactOf({ ...base, emergencyContact: { name: "Control room", phone: null } })).toBeNull();
    expect(emergencyContactOf(null)).toBeNull();
  });
  it("builds a tel: link from a formatted number", () => {
    expect(telUrl(" +27 (11) 555-0100 ")).toBe("tel:+27115550100");
    expect(telUrl("011 555 0100")).toBe("tel:0115550100");
  });
});
