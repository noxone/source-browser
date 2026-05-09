export interface UserAccount {
  id: number
  principalName: string
  admin: boolean
  serviceAccount: boolean
  createdAt: string
}

export interface UpdateUserAccountRequest {
  admin: boolean
}

export interface UserAccountPage {
  items: UserAccount[]
  totalItems: number
  page: number
  pageSize: number
  totalPages: number
}
