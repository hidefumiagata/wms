/**
 * リダイレクト先の安全性を検証する。
 * 相対パス（/始まりかつ //始まりでない）のみを許可し、
 * オープンリダイレクト攻撃（//attacker.com 等）を防ぐ。
 */
export function sanitizeRedirect(redirect: string | undefined): string {
  if (!redirect) return '/'
  // /で始まり、//で始まらない相対パスのみ許可
  if (redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/'
}
