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

import sbt._
import sbt.Keys._

object ColumnarFormatPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    // Re-export ColumnarSection so it is available in build.sbt without an explicit import
    type ColumnarSection = com.briskware.sbt.columnar.ColumnarSection
    val  ColumnarSection = com.briskware.sbt.columnar.ColumnarSection

    // Re-export ColumnarFormatterConfig so users can supply custom configs
    type ColumnarFormatterConfig = com.briskware.sbt.columnar.ColumnarFormatterConfig
    val  ColumnarFormatterConfig = com.briskware.sbt.columnar.ColumnarFormatterConfig

    // Re-export ColumnarConfig as the single root config users pass to the plugin
    type ColumnarConfig = com.briskware.sbt.columnar.ColumnarConfig
    val  ColumnarConfig = com.briskware.sbt.columnar.ColumnarConfig

    val columnarFmt = taskKey[Unit](
      "Format the columnar file: groups rows into sections, aligns columns, deduplicates"
    )

    val columnarFmtConfig = settingKey[ColumnarConfig](
      "Root configuration for the columnarFmt task. Wraps sections, lineLimit, fileGlob, " +
      "fileHeader, and formatterConfig in a single value."
    )
  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    columnarFmtConfig := ColumnarConfig.default,
    columnarFmt    := {
      val log   = streams.value.log
      val cfg   = columnarFmtConfig.value
      val files = ColumnarGlob.resolve(baseDirectory.value, cfg.fileGlob)
      if (files.isEmpty) {
        log.warn(s"No files matched: ${cfg.fileGlob}")
      } else {
        files.foreach { file =>
          IO.writeLines(file,
            ColumnarFormatter.reformat(
              IO.readLines(file),
              cfg.sections,
              cfg.lineLimit,
              cfg.fileHeader,
              cfg.formatterConfig
            )
          )
          log.info(s"[sbt-columnar-format] ${file.getName} formatted")
        }
      }
    }
  )
}
