import { describe, it, expect } from 'vitest'
import { sanitizeRedirect } from '@/utils/redirect'

describe('sanitizeRedirect', () => {
  it('returns "/" for undefined', () => {
    expect(sanitizeRedirect(undefined)).toBe('/')
  })

  it('returns "/" for empty string', () => {
    expect(sanitizeRedirect('')).toBe('/')
  })

  it('returns the path for a valid relative path starting with /', () => {
    expect(sanitizeRedirect('/dashboard')).toBe('/dashboard')
  })

  it('returns the path for a nested valid path', () => {
    expect(sanitizeRedirect('/inventory/list')).toBe('/inventory/list')
  })

  it('returns "/" for double-slash path (open redirect attack)', () => {
    expect(sanitizeRedirect('//attacker.com')).toBe('/')
  })

  it('returns "/" for http:// URL', () => {
    expect(sanitizeRedirect('http://evil.com')).toBe('/')
  })

  it('returns "/" for https:// URL', () => {
    expect(sanitizeRedirect('https://evil.com')).toBe('/')
  })

  it('returns "/" for a path without leading slash', () => {
    expect(sanitizeRedirect('dashboard')).toBe('/')
  })

  it('returns the path for root /', () => {
    expect(sanitizeRedirect('/')).toBe('/')
  })

  it('returns the path with query parameters', () => {
    expect(sanitizeRedirect('/search?q=test')).toBe('/search?q=test')
  })
})
