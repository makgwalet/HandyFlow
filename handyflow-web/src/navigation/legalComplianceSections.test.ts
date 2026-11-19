import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { LEGAL_COMPLIANCE_SECTIONS } from './moduleSections'

const ids = LEGAL_COMPLIANCE_SECTIONS.groups.flatMap(g => g.sections.map(s => s.id))

describe('Legal & Compliance sidebar', () => {
  it('has no duplicate section ids and a valid default', () => {
    expect(new Set(ids).size).toBe(ids.length)
    expect(ids).toContain(LEGAL_COMPLIANCE_SECTIONS.defaultSection)
  })

  it('has a screen for every section in the page switch', () => {
    const page = readFileSync('src/pages/legalcompliance/LegalCompliancePage.tsx', 'utf8')
    for (const id of ids) expect(page, `case "${id}"`).toContain(`case "${id}"`)
  })

  it('covers every tab the backend can send to the executive overview', () => {
    for (const id of ['obligations', 'controls', 'testing', 'findings', 'capa', 'risks', 'audits', 'policies', 'incidents', 'privacy', 'thirdparties', 'licences', 'committees', 'ethics', 'changes'])
      expect(ids).toContain(id)
  })

  it('hides the restricted sections behind permissions', () => {
    const by = (id: string) => LEGAL_COMPLIANCE_SECTIONS.groups.flatMap(g => g.sections).find(s => s.id === id)
    expect(by('fica')?.anyPermission).toContain('LEGALCOMPLIANCE_FICA_MANAGE')
    expect(by('providers')?.anyPermission).toContain('LEGALCOMPLIANCE_PROVIDER_MANAGE')
  })
})
