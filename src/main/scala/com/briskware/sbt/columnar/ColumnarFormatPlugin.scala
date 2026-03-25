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

    val columnarFmtCheck = taskKey[Unit](
      "Check that all columnar files are already formatted; fails if any file would be changed by columnarFmt"
    )

    val columnarFmtConfig = settingKey[Seq[ColumnarConfig]](
      "Configurations for the columnarFmt task. Each entry targets a distinct set of files " +
      "(via its fileGlob) and may use different sections, lineLimit, or formatterConfig."
    )
  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    columnarFmtConfig := Seq(ColumnarConfig.default),
    columnarFmt := {
      val log     = streams.value.log
      val baseDir = baseDirectory.value
      columnarFmtConfig.value.foreach { cfg =>
        val files = ColumnarGlob.resolve(baseDir, cfg.fileGlob)
        if (files.isEmpty) {
          log.warn(s"[sbt-columnar-format] No files matched: ${cfg.fileGlob}")
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
    },
    columnarFmtCheck := {
      val log     = streams.value.log
      val baseDir = baseDirectory.value
      val unformatted = columnarFmtConfig.value.flatMap { cfg =>
        val files = ColumnarGlob.resolve(baseDir, cfg.fileGlob)
        if (files.isEmpty) {
          log.warn(s"[sbt-columnar-format] No files matched: ${cfg.fileGlob}")
          Nil
        } else {
          files.filter { file =>
            val current    = IO.readLines(file)
            val reformatted = ColumnarFormatter.reformat(
              current,
              cfg.sections,
              cfg.lineLimit,
              cfg.fileHeader,
              cfg.formatterConfig
            )
            current != reformatted
          }
        }
      }
      if (unformatted.nonEmpty) {
        unformatted.foreach { file =>
          log.error(s"[sbt-columnar-format] ${file.getPath} is not formatted")
        }
        throw new MessageOnlyException(
          s"[sbt-columnar-format] ${unformatted.size} file(s) are not formatted. Run columnarFmt to fix."
        )
      } else {
        log.info("[sbt-columnar-format] All files are properly formatted")
      }
    }
  )
}
