import { describe, it, expect } from 'vitest'
import { FIREARM_TYPES, registerProblem } from './armouryLogic'

const ok = { firearmSerial: 'SN-1', firearmType: 'Handgun', sapsLicenseNumber: 'L1', licenseExpiry: '2027-01-01' }
describe('registerProblem', () => {
  it('accepts a complete form', () => expect(registerProblem(ok)).toBeNull())
  it('the type list is the three categories', () => expect([...FIREARM_TYPES]).toEqual(['Handgun', 'Shotgun', 'Rifle']))
  it('needs a type from the list', () => {
    expect(registerProblem({ ...ok, firearmType: '' })).toMatch(/type/)
    expect(registerProblem({ ...ok, firearmType: 'Glock 17' })).toMatch(/type/)
  })
  it('needs serial, license and expiry', () => {
    expect(registerProblem({ ...ok, firearmSerial: ' ' })).toMatch(/serial/)
    expect(registerProblem({ ...ok, sapsLicenseNumber: '' })).toMatch(/license number/)
    expect(registerProblem({ ...ok, licenseExpiry: '' })).toMatch(/expiry/)
  })
})
