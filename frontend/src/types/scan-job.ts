export interface ScanJob {
  id: number
  repositoryId: number
  triggerType: 'WEBHOOK' | 'CRON' | 'MANUAL'
  commitSha: string | null
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'
  queuedAt: string
  startedAt: string | null
  finishedAt: string | null
}
