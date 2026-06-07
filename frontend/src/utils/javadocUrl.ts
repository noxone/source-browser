import type { JavadocProvider } from '../types/javadoc-provider'

/**
 * Builds a Javadoc URL for the given symbol, or returns null if no configured
 * provider matches the qualified name's package prefix.
 *
 * URL template placeholders:
 *   {classPath} – FQN of the enclosing class with dots replaced by slashes
 *   {anchor}    – empty, or "#methodName(Param1,Param2)" for methods/constructors
 */
export function buildJavadocUrl(
  qualifiedName: string | null | undefined,
  kind: string,
  signature: string | null | undefined,
  providers: JavadocProvider[]
): string | null {
  if (!qualifiedName) return null
  const provider = findBestProvider(qualifiedName, providers)
  if (!provider) return null

  const classPath = resolveClassPath(qualifiedName, kind)
  const anchor = resolveAnchor(qualifiedName, kind, signature)

  return provider.urlTemplate
    .replace('{classPath}', classPath)
    .replace('{anchor}', anchor)
}

function findBestProvider(qualifiedName: string, providers: JavadocProvider[]): JavadocProvider | null {
  // Pick the provider with the longest matching prefix (most specific wins)
  let best: JavadocProvider | null = null
  for (const p of providers) {
    if (qualifiedName.startsWith(p.packagePrefix)) {
      if (!best || p.packagePrefix.length > best.packagePrefix.length) {
        best = p
      }
    }
  }
  return best
}

function resolveClassPath(qualifiedName: string, kind: string): string {
  const memberKinds = new Set(['METHOD', 'CONSTRUCTOR', 'FIELD', 'PARAMETER', 'LOCAL_VARIABLE'])
  let fqn = qualifiedName
  if (memberKinds.has(kind)) {
    // Strip the member name – everything up to (but not including) the last dot
    const lastDot = fqn.lastIndexOf('.')
    if (lastDot >= 0) fqn = fqn.substring(0, lastDot)
  }
  return fqn.replace(/\./g, '/')
}

function resolveAnchor(qualifiedName: string, kind: string, signature: string | null | undefined): string {
  if (kind !== 'METHOD' && kind !== 'CONSTRUCTOR') return ''

  // Simple name = last segment of qualified name
  const lastDot = qualifiedName.lastIndexOf('.')
  const simpleName = lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName

  if (!signature) return `#${simpleName}`

  // Extract parameter list from signature, e.g. "parse(String, int)" → "String,int"
  const parenOpen = signature.indexOf('(')
  const parenClose = signature.lastIndexOf(')')
  if (parenOpen < 0 || parenClose < 0) return `#${simpleName}`

  const params = signature.substring(parenOpen + 1, parenClose).trim()
  if (!params) return `#${simpleName}()`

  // Normalise: remove spaces around commas
  const normalised = params.split(',').map(p => p.trim()).join(',')
  return `#${simpleName}(${normalised})`
}
