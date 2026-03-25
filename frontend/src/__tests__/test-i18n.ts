import { createI18n } from 'vue-i18n'

// テスト用の最小限i18nインスタンス（翻訳キーをそのまま返す）
export const testI18n = createI18n({
  legacy: false,
  locale: 'ja',
  fallbackLocale: 'ja',
  missing: (_locale, key) => key,
  messages: { ja: {} },
})
