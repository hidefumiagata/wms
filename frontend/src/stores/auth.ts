import { defineStore } from 'pinia'
import { ref } from 'vue'
import apiClient from '@/api/client'

export type UserRole = 'SYSTEM_ADMIN' | 'WAREHOUSE_MANAGER' | 'WAREHOUSE_STAFF' | 'VIEWER'

export interface AuthUser {
  userId: number
  userCode: string
  fullName: string
  role: UserRole
  passwordChangeRequired: boolean
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const isAuthenticated = ref(false)

  async function login(userCode: string, password: string): Promise<AuthUser> {
    const response = await apiClient.post<AuthUser>('/auth/login', { userCode, password })
    user.value = response.data
    isAuthenticated.value = true
    return response.data
  }

  async function logout(): Promise<void> {
    await apiClient.post('/auth/logout').catch(() => {
      // サーバーエラーでもローカル状態はクリアする
    })
    user.value = null
    isAuthenticated.value = false
  }

  async function refresh(): Promise<boolean> {
    try {
      const response = await apiClient.post<AuthUser>('/auth/refresh')
      user.value = response.data
      isAuthenticated.value = true
      return true
    } catch {
      user.value = null
      isAuthenticated.value = false
      return false
    }
  }

  return { user, isAuthenticated, login, logout, refresh }
})
