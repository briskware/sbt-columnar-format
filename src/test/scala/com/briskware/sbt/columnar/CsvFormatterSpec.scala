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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Exercises [[ColumnarFormatter]] with a comma-separated metrics file.
  *
  * Format: `type,path,handler`
  * Example: `GAUGE,system.cpu,os.CpuMonitor.record`
  *
  * Sections are assigned by path prefix; output columns are comma-delimited.
  * This demonstrates that the formatter is fully generic — only the config changes.
  */
class CsvFormatterSpec extends AnyWordSpec with Matchers {

  private val csvConfig = ColumnarFormatterConfig.csv

  private val csvSections = Seq(
    ColumnarSection("# OS",  primaryPrefixes = Seq("system.")),
    ColumnarSection("# Web", primaryPrefixes = Seq("app.")),
    ColumnarSection("# Misc")   // catch-all
  )

  // ── Helpers ───────────────────────────────────────────────────────────────

  private def reformat(
    lines:      Seq[String],
    sections:   Seq[ColumnarSection] = Seq(ColumnarSection("# Metrics")),
    lineLimit:  Int                  = 999,
    fileHeader: String               = ""
  ): Seq[String] =
    ColumnarFormatter.reformat(lines, sections, lineLimit, fileHeader, csvConfig)

  private def sectionBefore(result: Seq[String], containing: String): String =
    result
      .take(result.indexWhere(_.contains(containing)))
      .filter(_.startsWith("#"))
      .last

  // ── Tests ─────────────────────────────────────────────────────────────────

  "CsvFormatter" when {

    "grouping records into sections" should {

      "place records in the section whose primary prefix matches" in {
        val result = reformat(
          Seq(
            "GAUGE,system.cpu,os.CpuMonitor.record",
            "COUNTER,app.requests,web.RequestTracker.count"
          ),
          sections = csvSections
        )
        sectionBefore(result, "CpuMonitor")      mustBe "# OS"
        sectionBefore(result, "RequestTracker")  mustBe "# Web"
      }

      "place unmatched records in the catch-all section" in {
        val result = reformat(
          Seq("GAUGE,infra.disk,storage.DiskMonitor.record"),
          sections = csvSections
        )
        sectionBefore(result, "DiskMonitor") mustBe "# Misc"
      }

      "omit sections that have no matching records" in {
        val result = reformat(
          Seq("GAUGE,system.cpu,os.CpuMonitor.record"),
          sections = csvSections
        )
        result must not contain "# Web"
        result must not contain "# Misc"
      }
    }

    "formatting with comma delimiter" should {

      "use comma as the column delimiter in output data lines" in {
        val result = reformat(Seq("GAUGE,system.cpu,os.CpuMonitor.record"))
        val dataLines = result.filterNot(l => l.startsWith("#") || l.isEmpty)
        dataLines must not be empty
        all(dataLines) must include(",")
      }
    }

    "deduplicating records" should {

      "remove duplicate (type, path) pairs keeping the first occurrence" in {
        val result = reformat(Seq(
          "GAUGE,system.cpu,os.CpuMonitor.record",
          "GAUGE,system.cpu,os.CpuMonitor.record"
        ))
        result.count(_.contains("system.cpu")) mustBe 1
      }

      "keep records with the same path but different types" in {
        val result = reformat(Seq(
          "GAUGE,system.cpu,os.CpuMonitor.record",
          "COUNTER,system.cpu,os.CpuMonitor.count"
        ))
        result.count(_.contains("system.cpu")) mustBe 2
      }
    }

    "inserting blank lines between subgroups" should {

      "insert exactly one blank line between two different handler subgroups" in {
        val result = reformat(Seq(
          "GAUGE,system.cpu,os.CpuMonitor.record",
          "COUNTER,system.cpu,os.CpuMonitor.count",
          "GAUGE,system.memory,os.MemMonitor.record",
          "COUNTER,system.memory,os.MemMonitor.count"
        ))
        val sectionLines = result.dropWhile(_ != "# Metrics").tail
        sectionLines(2) mustBe ""
        sectionLines(3) must include("MemMonitor")
      }

      "not insert blank lines within a single handler subgroup" in {
        val result = reformat(Seq(
          "GAUGE,system.cpu,os.CpuMonitor.record",
          "COUNTER,system.cpu,os.CpuMonitor.count",
          "RATE,system.cpu,os.CpuMonitor.rate"
        ))
        val sectionLines = result.dropWhile(_ != "# Metrics").tail
        sectionLines.filter(_.isEmpty) must be(empty)
      }
    }
  }
}
