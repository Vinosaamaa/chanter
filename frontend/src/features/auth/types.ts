export type AuthUser = {
  id: string
  email: string
  displayName: string
}

export type AuthSession = {
  accessToken: string
  expiresInSeconds: number
  user: AuthUser
}
