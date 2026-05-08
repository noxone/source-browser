export interface PersonalAccessToken {
  id: number
  name: string
  createdAt: string
  expiresAt: string | null
}

export interface PersonalAccessTokenCreated {
  token: PersonalAccessToken
  rawToken: string
}

export interface CreatePersonalAccessTokenRequest {
  name: string
  expiresAt: string | null
}
