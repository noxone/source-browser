import type { JavadocProvider } from '../types/javadoc-provider'
import { authenticatedFetch } from './http'

const BASE = '/api/javadoc-providers'

export async function listJavadocProviders(): Promise<JavadocProvider[]> {
  const response = await authenticatedFetch(BASE)
  if (!response.ok) throw new Error(`Failed to list Javadoc providers: ${response.status}`)
  return response.json()
}

export async function createJavadocProvider(
  data: Omit<JavadocProvider, 'id'>
): Promise<JavadocProvider> {
  const response = await authenticatedFetch(BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  if (!response.ok) throw new Error(`Failed to create Javadoc provider: ${response.status}`)
  return response.json()
}

export async function updateJavadocProvider(
  id: number,
  data: Omit<JavadocProvider, 'id'>
): Promise<JavadocProvider> {
  const response = await authenticatedFetch(`${BASE}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  if (!response.ok) throw new Error(`Failed to update Javadoc provider: ${response.status}`)
  return response.json()
}

export async function deleteJavadocProvider(id: number): Promise<void> {
  const response = await authenticatedFetch(`${BASE}/${id}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`Failed to delete Javadoc provider: ${response.status}`)
}
