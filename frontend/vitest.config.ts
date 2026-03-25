import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: ['./src/__tests__/setup.ts'],
    include: ['src/**/*.{test,spec}.ts'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/composables/**/*.ts', 'src/stores/**/*.ts', 'src/utils/**/*.ts'],
      reporter: ['text', 'text-summary'],
    },
  },
})
