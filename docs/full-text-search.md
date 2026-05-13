# Full-Text Search — Implementation Reference

This document explains how the full-text search works inside Source Viewer,
how to drive it directly with SQL, what configuration knobs are available,
and which PostgreSQL features would be worth exposing to end users.

---

## Table of Contents

1. [Schema overview](#1-schema-overview)
2. [How indexing works](#2-how-indexing-works)
3. [The current search query (annotated)](#3-the-current-search-query-annotated)
4. [Query parsers — choosing the right one](#4-query-parsers--choosing-the-right-one)
5. [Ranking functions and normalisation](#5-ranking-functions-and-normalisation)
6. [Snippet / headline options](#6-snippet--headline-options)
7. [Text-search configurations (dictionaries & stemming)](#7-text-search-configurations-dictionaries--stemming)
8. [Filtering and scoping results](#8-filtering-and-scoping-results)
9. [Performance notes](#9-performance-notes)
10. [Feature ideas worth exposing to users](#10-feature-ideas-worth-exposing-to-users)
11. [Quick-reference SQL cookbook](#11-quick-reference-sql-cookbook)

---

## 1. Schema overview

```sql
CREATE TABLE document (
    id            BIGSERIAL   PRIMARY KEY,
    file_id       BIGINT      NOT NULL REFERENCES source_file(id) ON DELETE CASCADE,
    document_type TEXT        NOT NULL,            -- always 'source' today
    content       TEXT        NOT NULL,            -- raw file text
    search_vector TSVECTOR    GENERATED ALWAYS AS  -- auto-maintained FTS index
                    (to_tsvector('english', content)) STORED,
    scan_job_id   BIGINT      REFERENCES scan_job(id),
    published     BOOLEAN     NOT NULL DEFAULT TRUE
);

-- GIN index makes @@ queries fast
CREATE INDEX idx_document_search_vector ON document USING GIN(search_vector);
```

The `search_vector` column is a **generated column**: PostgreSQL recomputes it
automatically whenever `content` changes. No application code needs to touch it.

The `published` flag guards against readers seeing partial scan state; only rows
with `published = true` are returned by the search query.

---

## 2. How indexing works

During a scan, `ExecuteScanJobService` calls:

```java
documentRepository.insertUnpublished(
    new Document(fileId, "source", content, job.identifier().value())
);
```

The raw file text is stored in `content`. PostgreSQL derives `search_vector`
automatically via the generated column expression:

```sql
to_tsvector('english', content)
```

`to_tsvector` tokenises the text, normalises tokens using the **`english`
dictionary** (lower-casing + Porter stemmer), strips stop-words, and writes the
result as a compact `TSVECTOR` value that is ready for GIN indexing.

After all files in a scan succeed, the application sets `published = true` for
the whole batch in a single transaction (two-phase commit), making the new
documents visible atomically.

---

## 3. The current search query (annotated)

```sql
SELECT d.file_id,

       -- ts_headline generates a short, keyword-highlighted excerpt
       ts_headline(
           'english',            -- dictionary
           d.content,            -- full source text
           plainto_tsquery('english', :text),   -- parsed query
           'MaxWords=30,MinWords=10,MaxFragments=1'
       ) AS snippet,

       -- ts_rank scores each match by term frequency
       ts_rank(
           d.search_vector,
           plainto_tsquery('english', :text)
       ) AS rank

FROM   document d
WHERE  d.published = true
  AND  d.search_vector @@ plainto_tsquery('english', :text)
ORDER  BY rank DESC
LIMIT  :limit OFFSET :offset;
```

Key parts:

| Part | Purpose |
|------|---------|
| `d.search_vector @@ …` | Boolean match — only rows containing the query |
| `plainto_tsquery` | Converts plain text to a query (implicit AND between words) |
| `ts_rank` | Computes a floating-point relevance score |
| `ts_headline` | Extracts and highlights matching fragments from `content` |
| `published = true` | Hides documents that are still being indexed |
| `ORDER BY rank DESC` | Most relevant files first |
| `LIMIT / OFFSET` | Pagination (default: max 50, offset 0) |

---

## 4. Query parsers — choosing the right one

PostgreSQL ships four query-parsing functions. They produce a `TSQUERY` value
that is matched against `search_vector` with `@@`.

### 4.1 `plainto_tsquery` (current)

```sql
plainto_tsquery('english', 'user service login')
-- → 'user' & 'servic' & 'login'
```

- All words are **AND**ed.
- Words are normalised (stemmed) the same way as the index.
- No boolean operators in user input; everything is treated as literal words.
- Safe for untrusted input.

### 4.2 `websearch_to_tsquery` ⭐ recommended upgrade

```sql
websearch_to_tsquery('english', 'user service -login')
-- → 'user' & 'servic' & !'login'

websearch_to_tsquery('english', '"user service"')
-- → 'user' <-> 'servic'   (phrase — words must be adjacent)

websearch_to_tsquery('english', 'user OR service')
-- → 'user' | 'servic'
```

Supports a **Google-like syntax** that users already know:

| Input | Meaning |
|-------|---------|
| `word1 word2` | Both words must appear (AND) |
| `word1 OR word2` | Either word (OR) |
| `-word` | Word must NOT appear |
| `"phrase words"` | Words must appear in sequence (phrase match) |

This is a **drop-in replacement** for `plainto_tsquery` with zero schema change.
It is available from **PostgreSQL 11** onward and is safe for untrusted input.

### 4.3 `phraseto_tsquery`

```sql
phraseto_tsquery('english', 'null pointer exception')
-- → 'null' <-> 'pointer' <-> 'except'
```

All words must appear in the **exact order**. Equivalent to quoting in
`websearch_to_tsquery`. Useful internally but not usually needed directly if
you use `websearch_to_tsquery`.

### 4.4 `to_tsquery` (advanced / internal use)

```sql
to_tsquery('english', 'UserServ:*')   -- prefix/wildcard
-- → 'userserv':*   matches UserService, UserServiceImpl, …

to_tsquery('english', 'user & (service | handler) & !test')
-- full boolean expression
```

- Supports the **`:*` suffix** for **prefix matching** — the only way to do
  wildcard searches in PostgreSQL FTS.
- Throws an error on malformed input — must be sanitised before use with
  untrusted data.
- Full boolean: `&` (AND), `|` (OR), `!` (NOT), `<->` (FOLLOWED BY / phrase),
  `<N>` (FOLLOWED BY with distance N).

> **Prefix search example:**
> ```sql
> WHERE search_vector @@ to_tsquery('english', 'UserSer:*')
> ```
> Matches files containing `UserService`, `UserServiceImpl`, `UserServlet`, etc.
> Because `'english'` applies stemming this may behave unexpectedly for short
> prefixes — consider using `'simple'` for code identifiers (see §7).

---

## 5. Ranking functions and normalisation

### 5.1 `ts_rank` (current)

Scores a match by the **frequency of query terms** in the document.

```sql
ts_rank(search_vector, query)           -- unweighted, no normalisation
ts_rank(search_vector, query, 1)        -- normalise by log(1 + doc length)
ts_rank(search_vector, query, 2)        -- normalise by doc length
ts_rank(search_vector, query, 32)       -- divide rank / (rank + 1) → [0, 1)
```

The third argument is a **normalisation bitmask** — values can be OR-ed:

| Bit | Effect |
|-----|--------|
| `0` | No normalisation (default) |
| `1` | Divide by `log(1 + #lexemes)` |
| `2` | Divide by `#lexemes` |
| `4` | Divide by mean harmonic distance between extents |
| `8` | Divide by `#unique lexemes` |
| `16` | Divide by `1 + log(#unique lexemes)` |
| `32` | Divide by `rank + 1`, ensuring result is in `[0, 1)` |

For large files (e.g. auto-generated code with many repeated tokens), **bit 1 or
bit 2** prevents them from always outranking shorter, more focused files.

### 5.2 `ts_rank_cd` — cover-density ranking

```sql
ts_rank_cd(search_vector, query)
ts_rank_cd(search_vector, query, 32)
```

Uses **cover density**: a document where the query terms appear close together
scores higher than one where they are scattered. This is generally better for
multi-word queries and is worth trying as a replacement for `ts_rank`.

### 5.3 Weight vectors

Both ranking functions accept an optional **weight vector** `ARRAY[D, C, B, A]`
(default `{0.1, 0.2, 0.4, 1.0}`). Weights correspond to token categories `D`
(lowest) through `A` (highest). These are used when the `search_vector` was
built with `setweight()`, allowing certain parts of a document (e.g., class
names) to rank higher than comments.

```sql
-- Example: index class names at weight A, rest at weight D
to_tsvector('english', class_names) ||
setweight(to_tsvector('english', full_source), 'D')

-- Then rank with custom weights
ts_rank(ARRAY[0.05, 0.1, 0.2, 1.0], search_vector, query)
```

This requires changing the indexing pipeline (building a composite
`search_vector`) but gives fine-grained control over what ranks highly.

---

## 6. Snippet / headline options

`ts_headline` generates the highlighted excerpt returned in `snippet`.
Options are passed as a comma-separated string in the fourth argument.

```sql
ts_headline(
    'english',
    d.content,
    query,
    'MaxWords=30, MinWords=10, MaxFragments=1,
     StartSel=<mark>, StopSel=</mark>,
     FragmentDelimiter= … '
)
```

| Option | Default | Meaning |
|--------|---------|---------|
| `MaxFragments` | `0` (= `1` fragment) | Max number of separate excerpts. `3` gives richer context when matches span multiple areas. |
| `MaxWords` | `35` | Maximum words per fragment (currently 30 in production) |
| `MinWords` | `15` | Minimum words per fragment (currently 10 in production) |
| `StartSel` | `<b>` | HTML tag placed **before** each highlighted term |
| `StopSel` | `</b>` | HTML tag placed **after** each highlighted term |
| `FragmentDelimiter` | ` … ` | Text inserted between fragments when `MaxFragments > 1` |
| `HighlightAll` | `false` | Highlight entire document (ignores `MaxWords`/`MinWords`) |
| `ShortWord` | `3` | Words shorter than this are not highlighted |

**Practical recommendations:**

- Change `StartSel` / `StopSel` to something the frontend can style, e.g.
  `StartSel=@@HL_START@@` and strip/replace on the client side to avoid raw
  HTML injection.
- Increase `MaxFragments` to `3` to show multiple matching areas per file —
  valuable when the search term appears in both a class declaration and a method body.
- Increase `MaxWords` to `50` when showing context inside the file viewer panel.

---

## 7. Text-search configurations (dictionaries & stemming)

The configuration name `'english'` passed to `to_tsvector`, `to_tsquery`, and
`ts_headline` controls tokenisation and normalisation.

```sql
SELECT cfgname FROM pg_ts_config;
-- arabic, danish, dutch, english, finnish, french, german,
-- hungarian, indonesian, irish, italian, norwegian, portuguese,
-- romanian, russian, simple, spanish, swedish, turkish
```

### `'english'` (current)

Applies the **Porter stemmer**: `running`, `runs`, `ran` all map to the same
lexeme `run`. This is good for prose text but **problematic for code**:

- Searching for `UserService` may not find `UserServices` (or vice versa) depending
  on how the stemmer behaves.
- Short identifiers like `get`, `set`, `is` are English stop-words and will be
  **silently dropped** from queries and index vectors, making them unsearchable.

```sql
SELECT to_tsvector('english', 'getUser setUser isActive');
-- 'activ':3 'getuser':1 'setuser':2
-- "get", "set", "is" stripped; "Active" → "activ"
```

### `'simple'` — recommended for code search

```sql
SELECT to_tsvector('simple', 'getUser setUser isActive');
-- 'getuser':1 'isactive':3 'setuser':2
-- all tokens kept, only lower-cased, no stemming, no stop-words
```

- No stemming: `running` ≠ `run` (exact lower-case matching).
- No stop-words: `get`, `set`, `is`, `do`, `new` are all searchable.
- Prefix search works predictably.
- **Best choice for identifiers, package names, and method names.**

> **Impact of switching to `'simple'`:** The `search_vector` generated column
> would need to be redefined, and the GIN index rebuilt, but no application code
> changes are needed beyond updating the config name string in
> `PanacheDocumentRepository`.

### Custom text-search configuration

For maximum control (e.g. special tokenisation rules for camelCase splitting),
a custom PostgreSQL text-search configuration can be created:

```sql
-- Example: create a 'code' configuration based on 'simple'
CREATE TEXT SEARCH CONFIGURATION code (COPY = simple);
-- Add a custom dictionary that splits camelCase if desired
```

This is advanced but enables searching for `User` to match inside `UserService`.

---

## 8. Filtering and scoping results

### 8.1 Filter by repository

The `document` table joins to `source_file` which has `repository_id`.
The domain model already supports a `repositoryIdentifier` filter in
`SearchQuery`, but the current SQL query does not apply it. Adding it is
a one-line join:

```sql
SELECT d.file_id,
       ts_headline('english', d.content, plainto_tsquery('english', :text),
                   'MaxWords=30,MinWords=10,MaxFragments=1') AS snippet,
       ts_rank(d.search_vector, plainto_tsquery('english', :text)) AS rank
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id
WHERE  d.published = true
  AND  d.search_vector @@ plainto_tsquery('english', :text)
  AND  sf.repository_id = :repositoryId   -- ← add this
ORDER  BY rank DESC
LIMIT  :limit OFFSET :offset;
```

### 8.2 Filter by document type

Currently only `'source'` is used, but the schema allows multiple document types
per file (e.g. a future `'javadoc'` or `'readme'` type). Filtering by type:

```sql
AND d.document_type = 'source'
```

### 8.3 Filter by file extension / language

Via the `source_file` join:

```sql
AND sf.language = 'java'
AND sf.path LIKE '%.xml'
```

### 8.4 Filter by path prefix

To scope search to a subdirectory:

```sql
AND sf.path LIKE 'src/main/java/com/example/%'
```

### 8.5 Combining conditions

```sql
WHERE  d.published = true
  AND  d.search_vector @@ websearch_to_tsquery('simple', :text)
  AND  sf.repository_id = :repositoryId
  AND  sf.language = 'java'
  AND  sf.path LIKE :pathPrefix || '%'
ORDER  BY ts_rank_cd(d.search_vector, websearch_to_tsquery('simple', :text), 32) DESC
LIMIT  :limit OFFSET :offset;
```

---

## 9. Performance notes

| Concern | Detail |
|---------|--------|
| GIN index | The `idx_document_search_vector` GIN index is used for `@@` matches. Ensure `VACUUM` runs regularly to keep GIN performance good on write-heavy tables. |
| `ts_headline` cost | `ts_headline` re-parses the raw `content` string at query time. For large files this can be slow. Consider caching snippets or limiting content size. |
| Index-only scans | Columns needed by the query (`file_id`, `published`) are not in the GIN index. A partial B-tree index on `(file_id) WHERE published = true` can help for the common filter. |
| `OFFSET` pagination | Deep offsets become slow because PostgreSQL must scan and discard earlier rows. Keyset pagination using the last-seen `rank` value is more efficient. |
| `ts_rank_cd` vs `ts_rank` | `ts_rank_cd` requires computing proximity — slightly more CPU. The difference is negligible for small result sets. |

---

## 10. Feature ideas worth exposing to users

The following are all achievable with minimal or no backend changes.

### 10.1 ⭐ Google-like search syntax (`websearch_to_tsquery`)

**What:** Replace `plainto_tsquery` with `websearch_to_tsquery`.  
**User experience:** Users can type:
- `UserService -Test` → files containing `UserService` but not `Test`
- `"null pointer"` → files with the exact phrase
- `login OR authentication` → files containing either term  

**Backend change:** One-line change in `PanacheDocumentRepository.search()`.
Optionally show a help tooltip in the search box.

---

### 10.2 ⭐ Prefix / wildcard search

**What:** Use `to_tsquery` with the `:*` suffix, or automatically append `:*` to
user input for "starts with" behaviour.  
**User experience:** Typing `UserSer` finds `UserService`, `UserServiceImpl`, etc.  
**Backend change:** Sanitise input (reject `'` and other special chars), append
`:*` before calling `to_tsquery`. Or combine: auto-detect a trailing `*` in
user input and use `to_tsquery`; otherwise use `websearch_to_tsquery`.

---

### 10.3 Repository scoping

**What:** Wire `SearchQuery.repositoryIdentifier` through to the SQL query.  
**User experience:** A dropdown in the UI narrows results to one repository.
The API already accepts the parameter; it just isn't used in the query yet.  
**Backend change:** Add the `JOIN source_file` and `AND sf.repository_id = :id`
clause to `PanacheDocumentRepository.search()`.

---

### 10.4 More / richer snippets per result (`MaxFragments`)

**What:** Increase `MaxFragments` from 1 to 3 in the `ts_headline` options string.  
**User experience:** Instead of one excerpt, show up to 3 separate matching
fragments from different parts of the file, separated by `…`.  
**Backend change:** One-word change to the options string. Optionally let the
API caller control the value.

---

### 10.5 Highlighted HTML snippets in the file viewer

**What:** When a search result is opened in the file viewer, scroll to the first
matching line and highlight the matching tokens.  
**User experience:** After clicking a result the file opens at the right location
with the searched terms already visible.  
**Backend change:** Pass the original query string to the file-view route; the
frontend can scan the token stream for tokens whose text matches and apply a
highlight CSS class.

---

### 10.6 `'simple'` dictionary for code identifiers

**What:** Change the text-search configuration from `'english'` to `'simple'`.  
**Why:** The English stemmer drops stop-words (`get`, `set`, `is`, `new`) and
conflates identifiers (`running`→`run`). `'simple'` uses exact lower-case
matching with no stop-words — much better for source code.  
**Backend change:** Update the config string in the generated column definition
(requires a migration), and mirror the change in the query calls.  
**Migration:**

```sql
ALTER TABLE document
  DROP COLUMN search_vector;
ALTER TABLE document
  ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;
DROP INDEX idx_document_search_vector;
CREATE INDEX idx_document_search_vector ON document USING GIN(search_vector);
```

---

### 10.7 Language / file-type filter

**What:** Let users narrow results to a specific language or path pattern.  
**User experience:** A "Language: Java / XML / All" toggle or a path-prefix
filter field.  
**Backend change:** Join `source_file` and add a `WHERE` clause; extend the
`SearchQuery` and `SearchResource` to accept the parameter.

---

### 10.8 Sorting options

**What:** Expose the choice between `ts_rank` (term frequency) and `ts_rank_cd`
(cover density — better for multi-word queries) as a sort option.  
**User experience:** A "Sort: relevance / best match" toggle.  
**Backend change:** Accept a `rankingMode` query parameter and switch the
`ORDER BY` expression.

---

## 11. Quick-reference SQL cookbook

All queries below can be run directly in `psql` or via `pgAdmin`.

```sql
-- ── 1. Basic search (mirrors current application behaviour) ─────────────────
SELECT sf.path,
       ts_headline('english', d.content,
                   plainto_tsquery('english', 'UserService'),
                   'MaxWords=30,MinWords=10,MaxFragments=1') AS snippet,
       ts_rank(d.search_vector, plainto_tsquery('english', 'UserService')) AS rank
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id
WHERE  d.published = true
  AND  d.search_vector @@ plainto_tsquery('english', 'UserService')
ORDER  BY rank DESC
LIMIT  20;


-- ── 2. Google-style search ────────────────────────────────────────────────────
SELECT sf.path, ts_rank_cd(d.search_vector, q, 32) AS rank
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id,
       websearch_to_tsquery('english', 'login OR authentication -test') q
WHERE  d.published = true
  AND  d.search_vector @@ q
ORDER  BY rank DESC
LIMIT  20;


-- ── 3. Prefix / wildcard search ───────────────────────────────────────────────
SELECT sf.path
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id
WHERE  d.published = true
  AND  d.search_vector @@ to_tsquery('english', 'UserSer:*')
ORDER  BY ts_rank(d.search_vector, to_tsquery('english', 'UserSer:*')) DESC;


-- ── 4. Phrase search (exact word order) ─────────────────────────────────────
SELECT sf.path
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id
WHERE  d.published = true
  AND  d.search_vector @@ phraseto_tsquery('english', 'null pointer exception');


-- ── 5. Scope to a specific repository ────────────────────────────────────────
SELECT sf.path, ts_rank(d.search_vector, plainto_tsquery('english', 'login')) AS rank
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id
JOIN   repository r   ON r.id = sf.repository_id
WHERE  d.published = true
  AND  r.name = 'my-service'
  AND  d.search_vector @@ plainto_tsquery('english', 'login')
ORDER  BY rank DESC;


-- ── 6. Multiple fragments per result ─────────────────────────────────────────
SELECT sf.path,
       ts_headline('english', d.content,
                   plainto_tsquery('english', 'authenticate'),
                   'MaxFragments=3,MaxWords=25,MinWords=8,FragmentDelimiter= … ') AS snippet
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id
WHERE  d.published = true
  AND  d.search_vector @@ plainto_tsquery('english', 'authenticate');


-- ── 7. See how a string is tokenised ─────────────────────────────────────────
SELECT to_tsvector('english', 'getUserById setActive isRunning');
SELECT to_tsvector('simple',  'getUserById setActive isRunning');
-- Compare: 'simple' keeps every token; 'english' stems and removes stop-words


-- ── 8. Inspect the search_vector of a specific file ──────────────────────────
SELECT d.search_vector
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id
WHERE  sf.path = 'src/main/java/com/example/UserService.java'
  AND  d.published = true;


-- ── 9. Count indexed documents per repository ────────────────────────────────
SELECT r.name, COUNT(*) AS doc_count
FROM   document d
JOIN   source_file sf ON sf.id = d.file_id
JOIN   repository r   ON r.id = sf.repository_id
WHERE  d.published = true
GROUP  BY r.name
ORDER  BY doc_count DESC;


-- ── 10. Check for stop-word casualties (terms silently dropped) ───────────────
SELECT to_tsvector('english', 'get set is new do try catch') AS english_result,
       to_tsvector('simple',  'get set is new do try catch') AS simple_result;
-- english_result: empty! all terms are English stop-words
-- simple_result:  all terms preserved
```

---

*Last updated: auto-generated from source analysis of `PanacheDocumentRepository`,
`ExecuteScanJobService`, migration files `V1_0__init.sql` and
`V1_12__two_phase_document_indexing.sql`.*
