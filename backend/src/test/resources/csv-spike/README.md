# C-4 spike fixtures and limitations

These are synthetic, byte-exact test fixtures for the physical-line-number spike.
They contain no supplied assignment data.

## Fixture inventory

| Fixture | Line endings | Proves |
|---|---|---|
| `ordinary-lf.csv`, `ordinary-crlf.csv` | LF / CRLF | Header = line 1; ordinary records |
| `blank-lines-lf.csv` | LF | Blank physical lines count |
| `embedded-newline-lf.csv`, `embedded-newline-crlf.csv` | LF / CRLF | Newlines inside quoted fields count |
| `malformed-after-embedded-lf.csv`, `malformed-after-embedded-crlf.csv` | LF / CRLF | Wrong-field-count row after embedded newline, valid row after it |
| `mixed-endings.csv` | mixed | LF and CRLF in one file |
| `lone-cr.csv` | lone CR | CR-only separators, blank CR line, embedded CR in quotes |
| `unicode-supplementary.csv` | LF | Emoji surrogate pair + multibyte characters; character-position alignment |

## Approach

`CSVRecord.getCharacterPosition()` (Commons CSV 1.13+) is captured by `CSVParser.nextRecord()`
before blank lines are skipped. `PhysicalLineMap` independently maps each character index to a
physical line, counting LF, CRLF and lone CR. Leading CR/LF characters at the reported position
are skipped because the lexer may report the start of a blank line rather than the record's
first content character.

## Limitations — carry into production ingestion design

1. **Unterminated quoted fields abort parsing.** Commons CSV throws an `UncheckedIOException`
   wrapping `CSVException` (for example, `(startline 2) EOF reached before encapsulated token
   finished`). No `CSVRecord` is produced for the failing row, so no physical line can be
   assigned by this mapper. Production ingestion must treat this as a **file-level failure**:
   batch `FAILED`, no partial student writes, and a clear administrator-facing message.

2. **CSV comment markers remain disabled.** The proof uses `CSVFormat.DEFAULT` (no comment
   marker). With `setCommentMarker('#')`, `getCharacterPosition()` points at the comment line
   rather than the following record, so a record after a comment would be assigned the wrong
   physical line. **Do not enable comment markers** without extending and re-testing the
   physical-line mapping.

3. **`PhysicalLineCsvReader.read(Path)` strips a leading UTF-8 BOM** before building both the
   line map and the parser input, so character positions stay aligned.

4. **`PhysicalLineCsvReader.read(String)` expects BOM handling to have been performed by its
   caller.** It does not strip a BOM itself. Production callers must strip the BOM before
   passing text to the mapper/parser.

5. Character positions are UTF-16 code-unit offsets relative to the decoded character sequence;
   supplementary characters occupy two positions. The map and parser must always be built from
   the same decoded source.

6. `CSVRecord.isConsistent()` returns `true` when no header map is configured; the spike computes
   field-count consistency against the header record explicitly. Production validation must not
   rely on `isConsistent()` without configuring a header.
