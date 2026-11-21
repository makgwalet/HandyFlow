import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { competencyFormError, firearmCategoryLabel } from './guard360.logic'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
import SkillsTab from './GuardCompetencyPanel'

afterEach(() => cleanup())
beforeEach(() => vi.clearAllMocks())

describe('competencyFormError for firearm competency', () => {
  const base = { competencyType: 'FIREARM_COMPETENCY', title: '', issueDate: '', expiryDate: '2027-01-01', firearmCategory: 'HANDGUN' }
  it('passes with a category and an expiry', () => expect(competencyFormError(base, '2026-11-20')).toBeNull())
  it('needs a category', () => expect(competencyFormError({ ...base, firearmCategory: '' }, '2026-11-20')).toMatch(/firearm category/i))
  it('needs an expiry', () => expect(competencyFormError({ ...base, expiryDate: '' }, '2026-11-20')).toMatch(/expiry date/i))
  it('does not apply to other types', () => expect(competencyFormError({ ...base, competencyType: 'FIRST_AID', firearmCategory: '', expiryDate: '' }, '2026-11-20')).toBeNull())
})

describe('firearmCategoryLabel', () => { it('reads', () => { expect(firearmCategoryLabel('RIFLE')).toBe('Rifle'); expect(firearmCategoryLabel(null)).toBeNull() }) })

describe('Skills tab firearm form', () => {
  it('asks for the category and sends it', async () => {
    api.post.mockResolvedValue({ data: {} })
    render(<QueryClientProvider client={new QueryClient()}><SkillsTab guardId="g1" competencies={[]} canManage /></QueryClientProvider>)
    fireEvent.click(screen.getByRole('button', { name: /Add competency/ }))
    fireEvent.change(await screen.findByLabelText('Competency'), { target: { value: 'FIREARM_COMPETENCY' } })
    fireEvent.change(screen.getByLabelText('Expiry date'), { target: { value: '2027-06-30' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Add competency' }).pop()!)
    expect(await screen.findByRole('alert')).toBeTruthy()          // no category yet
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('Firearm category'), { target: { value: 'SHOTGUN' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Add competency' }).pop()!)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/guards/g1/competencies',
      expect.objectContaining({ competencyType: 'FIREARM_COMPETENCY', firearmCategory: 'SHOTGUN', expiryDate: '2027-06-30' })))
  })
})
