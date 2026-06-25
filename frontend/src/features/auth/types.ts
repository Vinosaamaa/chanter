export type AuthUser = {
  id: string
  email: string
  displayName: string
}

export type AuthSession = {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
  user: AuthUser
}
