/*
 * Copyright 2026 Briskware Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.briskware.sbt.columnar

/** Wires a concrete file format into [[ColumnarFormatter]].
  *
  * @param parse        Parses one input line into an ordered sequence of column values,
  *                     or returns `None` to skip the line (blanks, comments, etc.).
  * @param primaryCol   Index of the column used as the primary section-matching key
  *                     (longest-prefix wins).
  * @param secondaryCol Index of the column used as the fallback section-matching key.
  * @param dedupeKey    Derives the key used for duplicate-row detection; rows sharing a
  *                     key after the first occurrence are dropped.
  * @param subkeyFn     Derives the subgroup key used to insert blank lines between
  *                     consecutive runs of rows with the same value.
  * @param delimiter    String placed between adjacent columns in output lines.
  *                     Defaults to two spaces.
  */
final case class ColumnarFormatterConfig(
  parse:        String => Option[IndexedSeq[String]],
  primaryCol:   Int,
  secondaryCol: Int,
  dedupeKey:    IndexedSeq[String] => Any,
  subkeyFn:     IndexedSeq[String] => String,
  delimiter:    String = "  "
)

object ColumnarFormatterConfig {

  /** CSV: comma-separated `col0,col1,col2` with col1 as the primary section key. */
  val csv: ColumnarFormatterConfig = delimited(",")

  /** TSV: tab-separated `col0\tcol1\tcol2` with col1 as the primary section key. */
  val tsv: ColumnarFormatterConfig = delimited("\t")

  /** Pipe-delimited: `col0|col1|col2` parsed on `|`, output formatted as `col0 | col1 | col2`. */
  val pipeDelimited: ColumnarFormatterConfig = delimited(parseSep = "|", outputDelim = " | ")

  /** Play Framework `.routes` files: whitespace-delimited `method  path  controller`. */
  val playRoutes: ColumnarFormatterConfig = ColumnarFormatterConfig(
    parse        = parseWhitespace,
    primaryCol   = 1,
    secondaryCol = 2,
    dedupeKey    = cols => (cols(0), cols(1)),
    subkeyFn     = cols => prefixClass(cols(2))
  )

  // ── Private helpers ───────────────────────────────────────────────────────

  // Builds a config for a simple 3-column format with a fixed separator character.
  // parseSep is used for parsing; outputDelim is used for formatted output (defaults to parseSep).
  private def delimited(parseSep: String, outputDelim: String = null): ColumnarFormatterConfig = {
    val delim = if (outputDelim == null) parseSep else outputDelim
    ColumnarFormatterConfig(
      parse        = parseDelimited(parseSep),
      primaryCol   = 1,
      secondaryCol = 2,
      dedupeKey    = cols => (cols(0), cols(1)),
      subkeyFn     = cols => prefixClass(cols(2)),
      delimiter    = delim
    )
  }

  private def parseWhitespace(line: String): Option[IndexedSeq[String]] = {
    val t = line.trim
    if (t.isEmpty || t.startsWith("#")) None
    else {
      val parts = t.split("\\s+", 3)
      if (parts.length == 3) Some(parts.toIndexedSeq) else None
    }
  }

  private def parseDelimited(sep: String)(line: String): Option[IndexedSeq[String]] = {
    val t = line.trim
    if (t.isEmpty || t.startsWith("#")) None
    else {
      val parts = t.split(java.util.regex.Pattern.quote(sep), 3).map(_.trim)
      if (parts.length == 3) Some(parts.toIndexedSeq) else None
    }
  }

  // Extracts the class prefix from a dotted name, stripping trailing method/args, e.g.
  //   "controllers.AlphaController.onPageLoad()" -> "controllers.AlphaController"
  //   "os.CpuMonitor.record"                    -> "os.CpuMonitor"
  private def prefixClass(col: String): String = {
    val beforeParen = col.takeWhile(_ != '(')
    val parts       = beforeParen.split("\\.")
    parts.dropRight(1).mkString(".")
  }
}
