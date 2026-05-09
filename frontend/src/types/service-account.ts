import type { PersonalAccessToken, PersonalAccessTokenCreated, CreatePersonalAccessTokenRequest } from './personal-access-token'

export interface ServiceAccount {
  id: number
  name: string
  admin: boolean
  createdAt: string
}

export interface CreateServiceAccountRequest {
  name: string
  admin: boolean
}

export interface UpdateServiceAccountRequest {
  admin: boolean
}

export type ServiceAccountToken = PersonalAccessToken
export type ServiceAccountTokenCreated = PersonalAccessTokenCreated
export type CreateServiceAccountTokenRequest = CreatePersonalAccessTokenRequest
