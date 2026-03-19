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

/** Defines a named section in a formatted columnar file.
  *
  * Rows are assigned to a section by matching rules applied in priority order:
  *   1. Longest matching `primaryPrefix` wins (handles specificity automatically).
  *   2. If no primary prefix matches, the first matching `secondaryPrefix` is used.
  *   3. If nothing matches, the row falls to the catch-all section — the first
  *      section with both `primaryPrefixes` and `secondaryPrefixes` empty.
  *
  * @param header             The comment header written above the section, e.g. `# Company`.
  * @param primaryPrefixes    Primary-column prefixes that assign rows to this section.
  * @param secondaryPrefixes  Secondary-column prefixes used as a fallback when no
  *                           primary prefix matches.
  * @param lineLimit          Per-section override for the global line limit.
  *                           When set, this section uses its own threshold to decide
  *                           between section-wide and per-subgroup column alignment.
  */
final case class ColumnarSection(
  header:             String,
  primaryPrefixes:    Seq[String] = Seq.empty,
  secondaryPrefixes:  Seq[String] = Seq.empty,
  lineLimit:          Option[Int] = None
)
