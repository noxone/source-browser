export type GitProviderType = 'GITLAB' | 'GITHUB'

export interface GitProviderGroup {
  id: number
  name: string
  providerType: GitProviderType
  groupPath: string
  baseUrl: string | null
  archivedOmitted: boolean
  forkedOmitted: boolean
  sharedOmitted: boolean
  importedOmitted: boolean
  repositoryCount: number
}

export interface CreateGitProviderGroupRequest {
  name: string
  providerType: GitProviderType
  groupPath: string
  baseUrl: string | null
  archivedOmitted: boolean
  forkedOmitted: boolean
  sharedOmitted: boolean
  importedOmitted: boolean
}

export interface UpdateGitProviderGroupRequest {
  name: string
  providerType: GitProviderType
  groupPath: string
  baseUrl: string | null
  archivedOmitted: boolean
  forkedOmitted: boolean
  sharedOmitted: boolean
  importedOmitted: boolean
}
