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

class TsvFormatterSpec extends AnyWordSpec with Matchers {

  private def reformat(
    lines:      Seq[String],
    sections:   Seq[ColumnarSection] = Seq(ColumnarSection("# Records")),
    lineLimit:  Int                  = 999,
    fileHeader: String               = ""
  ): Seq[String] =
    ColumnarFormatter.reformat(lines, sections, lineLimit, fileHeader,
      ColumnarFormatterConfig.tsv)

  private val T = "\t"

  private val tsvSections = Seq(
    ColumnarSection("# OS",  primaryPrefixes = Seq("system.")),
    ColumnarSection("# Web", primaryPrefixes = Seq("app.")),
    ColumnarSection("# Misc")
  )

  "TsvFormatter" when {

    "parsing tab-separated input" should {

      "parse lines split by tab" in {
        val result = reformat(Seq(s"GAUGE${T}system.cpu${T}os.CpuMonitor.record"))
        result.filterNot(l => l.startsWith("#") || l.isEmpty) must not be empty
      }

      "skip blank lines and comment lines" in {
        val result = reformat(Seq(
          "",
          "# a comment",
          s"GAUGE${T}system.cpu${T}os.CpuMonitor.record"
        ))
        result.count(_.contains("system.cpu")) mustBe 1
      }
    }

    "grouping records into sections" should {

      "place records in the section whose primary prefix matches" in {
        val result = reformat(
          Seq(
            s"GAUGE${T}system.cpu${T}os.CpuMonitor.record",
            s"COUNTER${T}app.requests${T}web.RequestTracker.count"
          ),
          sections = tsvSections
        )
        val headers = result.filter(_.startsWith("#"))
        headers must contain("# OS")
        headers must contain("# Web")
        result.indexOf("# OS") must be < result.indexWhere(_.contains("CpuMonitor"))
        result.indexOf("# Web") must be < result.indexWhere(_.contains("RequestTracker"))
      }

      "place unmatched records in the catch-all section" in {
        val result = reformat(
          Seq(s"GAUGE${T}infra.disk${T}storage.DiskMonitor.record"),
          sections = tsvSections
        )
        result.filter(_.startsWith("#")) mustBe Seq("# Misc")
      }
    }

    "formatting output" should {

      "use tab as the column delimiter in output data lines" in {
        val result = reformat(Seq(s"GAUGE${T}system.cpu${T}os.CpuMonitor.record"))
        val dataLines = result.filterNot(l => l.startsWith("#") || l.isEmpty)
        all(dataLines) must include(T)
      }

      "align col1 across rows in the same section" in {
        val result = reformat(Seq(
          s"GAUGE${T}system.cpu${T}os.CpuMonitor.record",
          s"GAUGE${T}system.memory${T}os.MemMonitor.record"
        ))
        val dataLines = result.filterNot(l => l.startsWith("#") || l.isEmpty)
        // Both rows belong to the same subgroup — check col2 starts at the same position
        dataLines.map(_.indexOf("os.")).distinct must have size 1
      }
    }

    "deduplicating records" should {

      "remove duplicate (col0, col1) pairs keeping the first occurrence" in {
        val result = reformat(Seq(
          s"GAUGE${T}system.cpu${T}os.CpuMonitor.record",
          s"GAUGE${T}system.cpu${T}os.CpuMonitor.record"
        ))
        result.count(_.contains("system.cpu")) mustBe 1
      }
    }

    "inserting blank lines between subgroups" should {

      "insert a blank line between different handler subgroups" in {
        val result = reformat(Seq(
          s"GAUGE${T}system.cpu${T}os.CpuMonitor.record",
          s"COUNTER${T}system.cpu${T}os.CpuMonitor.count",
          s"GAUGE${T}system.memory${T}os.MemMonitor.record",
          s"COUNTER${T}system.memory${T}os.MemMonitor.count"
        ))
        val sectionLines = result.dropWhile(_ != "# Records").tail
        sectionLines(2) mustBe ""
        sectionLines(3) must include("MemMonitor")
      }
    }
  }
}
