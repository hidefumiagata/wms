import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { mockAxiosResponse } from '../helpers'
import { useAuthStore, type AuthUser } from '@/stores/auth'

const mockUser: AuthUser = {
  userId: 1,
  userCode: 'USR001',
  fullName: 'Test User',
  role: 'WAREHOUSE_MANAGER',
  passwordChangeRequired: false,
}

describe('useAuthStore', () => {
  let store: ReturnType<typeof useAuthStore>

  beforeEach(() => {
    store = useAuthStore()
  })

  describe('initial state', () => {
    it('user is null', () => {
      expect(store.user).toBeNull()
    })

    it('isAuthenticated is false', () => {
      expect(store.isAuthenticated).toBe(false)
    })
  })

  describe('login', () => {
    it('sets user and isAuthenticated on success', async () => {
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(mockUser))

      const result = await store.login('USR001', 'password123')

      expect(apiClient.post).toHaveBeenCalledWith('/auth/login', {
        userCode: 'USR001',
        password: 'password123',
      })
      expect(result).toEqual(mockUser)
      expect(store.user).toEqual(mockUser)
      expect(store.isAuthenticated).toBe(true)
    })

    it('throws and leaves state unchanged on failure', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Unauthorized'))

      await expect(store.login('USR001', 'wrong')).rejects.toThrow('Unauthorized')
      expect(store.user).toBeNull()
      expect(store.isAuthenticated).toBe(false)
    })
  })

  describe('logout', () => {
    beforeEach(async () => {
      // Set up authenticated state
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(mockUser))
      await store.login('USR001', 'password123')
      vi.clearAllMocks()
    })

    it('clears user and isAuthenticated on success', async () => {
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(null))

      await store.logout()

      expect(apiClient.post).toHaveBeenCalledWith('/auth/logout')
      expect(store.user).toBeNull()
      expect(store.isAuthenticated).toBe(false)
    })

    it('clears state even when API returns error', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Server Error'))

      await store.logout()

      expect(store.user).toBeNull()
      expect(store.isAuthenticated).toBe(false)
    })
  })

  describe('refresh', () => {
    it('sets user and returns true on success', async () => {
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(mockUser))

      const result = await store.refresh()

      expect(apiClient.post).toHaveBeenCalledWith('/auth/refresh')
      expect(result).toBe(true)
      expect(store.user).toEqual(mockUser)
      expect(store.isAuthenticated).toBe(true)
    })

    it('clears user and returns false on failure', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Token expired'))

      const result = await store.refresh()

      expect(result).toBe(false)
      expect(store.user).toBeNull()
      expect(store.isAuthenticated).toBe(false)
    })
  })

  describe('clearPasswordChangeRequired', () => {
    it('sets passwordChangeRequired to false when user exists', async () => {
      const userWithPwChange: AuthUser = { ...mockUser, passwordChangeRequired: true }
      vi.mocked(apiClient.post).mockResolvedValue(mockAxiosResponse(userWithPwChange))
      await store.login('USR001', 'password123')

      store.clearPasswordChangeRequired()

      expect(store.user!.passwordChangeRequired).toBe(false)
    })

    it('does nothing when user is null', () => {
      store.clearPasswordChangeRequired()

      expect(store.user).toBeNull()
    })
  })
})
