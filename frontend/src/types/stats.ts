export interface IndexedRepoStats {
  id: number
  name: string
  fileCount: number
}

export interface IndexStats {
  repositories: IndexedRepoStats[]
  totalFiles: number
  totalDocuments: number
  totalSymbols: number
  totalReferences: number
}
