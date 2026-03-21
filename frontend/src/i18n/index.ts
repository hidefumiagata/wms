import { createI18n } from 'vue-i18n'
import ja from './locales/ja'
import en from './locales/en'

export type MessageSchema = typeof ja

const i18n = createI18n<[MessageSchema], 'ja' | 'en'>({
  legacy: false,
  locale: 'ja',
  fallbackLocale: 'en',
  messages: { ja, en },
})

export default i18n
