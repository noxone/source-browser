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
  /** Qualified name (present for symbol declarations / references) */
  q?: string
  /** Symbol ID (present for symbol declarations / references) */
  s?: number
}

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

/** Result of an LSP hover + definition query for a token position. */
export interface LspHoverResult {
  markdownContent: string | null
  definitionFilePath: string | null
  definitionLine: number | null
  definitionColumn: number | null
}
