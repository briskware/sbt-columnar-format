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

/** Top-level configuration passed to the `columnarFmt` task.
  *
  * @param sections        Ordered list of sections; see [[ColumnarSection]].
  * @param lineLimit        Maximum line length before switching to per-subgroup alignment.
  * @param fileGlob         Glob pattern (relative to `baseDirectory`) for files to format.
  * @param fileHeader       Optional comment written as the first line of the formatted file.
  * @param formatterConfig  Controls how input lines are parsed and output is delimited.
  */
final case class ColumnarConfig(
  sections:        Seq[ColumnarSection]    = Seq(ColumnarSection("# Default")),
  lineLimit:       Int                     = 120,
  fileGlob:        String                  = "**/*.csv",
  fileHeader:      String                  = "",
  formatterConfig: ColumnarFormatterConfig = ColumnarFormatterConfig.csv
)

object ColumnarConfig {
  val default: ColumnarConfig = ColumnarConfig()
}