export interface GitCredential {
  id: number
  description: string | null
  updatedAt: string
}

export interface SetCredentialRequest {
  description: string | null
  secret: string
}
