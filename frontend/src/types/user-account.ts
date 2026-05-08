export interface UserAccount {
  id: number
  principalName: string
  admin: boolean
  createdAt: string
}

export interface UpdateUserAccountRequest {
  admin: boolean
}
