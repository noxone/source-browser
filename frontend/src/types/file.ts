export interface FileInfo {
  fileId: number
  filePath: string
  repositoryName: string | null
  branch: string
  language: string
  indexedAt: string
  hasTokenStream: boolean
  lastCommitSha: string | null
  lastAuthorName: string | null
  lastAuthorEmail: string | null
  lastCommitDate: string | null
  lastCommitMessage: string | null
  fileSize: number | null
  repositoryUrl: string | null
}

/** A single lexical token from the compressed token stream. */
export interface Token {
  /** Line number (1-based) */
  l: number
  /** Column start */
  cs: number
  /** Column end */
  ce: number
  /** Token text */
  t: string
  /** Token kind */
  k: TokenKind
  /** Import group ID — tokens with the same g belong to one import/include statement */
  g?: number
  /** Highlight group ID — all tokens in the file with the same hg value are highlighted on click */
  hg?: number
  /** True if this token has a detail entry and is clickable */
  d?: boolean
}

export interface MethodParam {
  name: string
  type: string
}

export interface MethodOverload {
  signature: string
  filePath?: string
  repositoryName?: string
  lineStart?: number
}

export interface MethodImpl {
  qualifiedName: string
  fileId: number
  filePath: string | null
  repositoryName: string | null
  lineStart?: number
}

export interface TypeLocation {
  qualifiedName: string
  fileId?: number
  filePath?: string
  repositoryName?: string
  lineStart?: number
}

export type TokenDetail =
  | { detailType: 'TYPE_REF' | 'TYPE_DECL'; qualifiedName: string; kind: string; fileId?: number; filePath?: string; repositoryName?: string; lineStart?: number; superclassFqn?: string; superclassFileId?: number; superclassFilePath?: string; superclassRepositoryName?: string; superclassLineStart?: number; implementedInterfaces?: TypeLocation[]; knownSubtypes?: Array<TypeLocation & { relationshipKind: string }> }
  | { detailType: 'VARIABLE'; name: string; variableKind: string; typeFqn: string | null; typeFileId?: number; typeFilePath?: string; typeRepositoryName?: string; typeLineStart?: number }
  | { detailType: 'METHOD_CALL' | 'METHOD_DECL'; name: string; declaringClass: string; returnType: string; parameters: MethodParam[]; overloads: MethodOverload[]; implementations: MethodImpl[]; isConstructor?: boolean }
  | { detailType: 'ANNOTATION'; qualifiedName: string }
  | { detailType: 'KEYWORD'; keyword: string; description: string | null }

export type TokenKind =
  | 'KEYWORD'
  | 'IDENTIFIER'
  | 'STRING_LITERAL'
  | 'CHAR_LITERAL'
  | 'INTEGER_LITERAL'
  | 'LONG_LITERAL'
  | 'FLOAT_LITERAL'
  | 'DOUBLE_LITERAL'
  | 'LINE_COMMENT'
  | 'BLOCK_COMMENT'
  | 'JAVADOC_COMMENT'
  | 'OPERATOR'
  | 'SEPARATOR'
  | 'WHITESPACE'
  | 'OTHER'

export interface SymbolInfo {
  symbolId: number
  fileId: number
  filePath: string | null
  repositoryName: string | null
  qualifiedName: string
  simpleName: string
  kind: string
  signature: string | null
  lineStart: number | null
  lineEnd: number | null
}

export interface SymbolReference {
  referenceId: number
  fileId: number
  filePath: string | null
  repositoryName: string | null
  kind: string
  line: number | null
  columnStart: number | null
}
