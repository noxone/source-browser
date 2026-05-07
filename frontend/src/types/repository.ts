export interface Repository {
  id: number
  name: string
  remoteUrl: string | null
  localPath: string
  defaultBranch: string
  lastScannedAt: string | null
  lastCommitSha: string | null
}

export interface CreateRepositoryRequest {
  name: string
  remoteUrl: string | null
  localPath: string
  defaultBranch: string
}

export interface UpdateRepositoryRequest {
  name: string
  remoteUrl: string | null
  localPath: string
  defaultBranch: string
}
