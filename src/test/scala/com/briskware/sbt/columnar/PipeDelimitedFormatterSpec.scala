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

class PipeDelimitedFormatterSpec extends AnyWordSpec with Matchers {

  private def reformat(
    lines:      Seq[String],
    sections:   Seq[ColumnarSection] = Seq(ColumnarSection("# Records")),
    lineLimit:  Int                  = 999,
    fileHeader: String               = ""
  ): Seq[String] =
    ColumnarFormatter.reformat(lines, sections, lineLimit, fileHeader,
      ColumnarFormatterConfig.pipeDelimited)

  private val pipeSections = Seq(
    ColumnarSection("# OS",  primaryPrefixes = Seq("system.")),
    ColumnarSection("# Web", primaryPrefixes = Seq("app.")),
    ColumnarSection("# Misc")
  )

  "PipeDelimitedFormatter" when {

    "parsing pipe-separated input" should {

      "parse lines split by bare pipe" in {
        val result = reformat(Seq("GAUGE|system.cpu|os.CpuMonitor.record"))
        result.filterNot(l => l.startsWith("#") || l.isEmpty) must not be empty
      }

      "also accept pipes surrounded by spaces (trims each column)" in {
        // Input with spaces around pipes should produce the same result
        val bareResult   = reformat(Seq("GAUGE|system.cpu|os.CpuMonitor.record"))
        val spacedResult = reformat(Seq("GAUGE | system.cpu | os.CpuMonitor.record"))
        bareResult mustBe spacedResult
      }

      "skip blank lines and comment lines" in {
        val result = reformat(Seq(
          "",
          "# a comment",
          "GAUGE|system.cpu|os.CpuMonitor.record"
        ))
        result.count(_.contains("system.cpu")) mustBe 1
      }
    }

    "grouping records into sections" should {

      "place records in the section whose primary prefix matches" in {
        val result = reformat(
          Seq(
            "GAUGE|system.cpu|os.CpuMonitor.record",
            "COUNTER|app.requests|web.RequestTracker.count"
          ),
          sections = pipeSections
        )
        result.indexOf("# OS")  must be < result.indexWhere(_.contains("CpuMonitor"))
        result.indexOf("# Web") must be < result.indexWhere(_.contains("RequestTracker"))
      }

      "output sections in definition order regardless of input order" in {
        val result = reformat(
          Seq(
            "COUNTER|app.requests|web.RequestTracker.count",
            "GAUGE|system.cpu|os.CpuMonitor.record"
          ),
          sections = pipeSections
        )
        result.filter(_.startsWith("#")) mustBe Seq("# OS", "# Web")
      }
    }

    "formatting output" should {

      "use ' | ' as the column delimiter in output data lines" in {
        val result = reformat(Seq("GAUGE|system.cpu|os.CpuMonitor.record"))
        val dataLines = result.filterNot(l => l.startsWith("#") || l.isEmpty)
        all(dataLines) must include(" | ")
      }

      "align col1 section-wide when all rows fit within the line limit" in {
        val result = reformat(Seq(
          "GAUGE|system.cpu|os.CpuMonitor.record",
          "GAUGE|system.memory|os.MemMonitor.record"
        ))
        val dataLines = result.filterNot(l => l.startsWith("#") || l.isEmpty)
        dataLines.map(_.indexOf("os.")).distinct must have size 1
      }
    }

    "deduplicating records" should {

      "remove duplicate (col0, col1) pairs keeping the first occurrence" in {
        val result = reformat(Seq(
          "GAUGE|system.cpu|os.CpuMonitor.record",
          "GAUGE|system.cpu|os.CpuMonitor.record"
        ))
        result.count(_.contains("system.cpu")) mustBe 1
      }

      "keep records with the same col1 but different col0" in {
        val result = reformat(Seq(
          "GAUGE|system.cpu|os.CpuMonitor.record",
          "COUNTER|system.cpu|os.CpuMonitor.count"
        ))
        result.count(_.contains("system.cpu")) mustBe 2
      }
    }

    "inserting blank lines between subgroups" should {

      "insert a blank line between different handler subgroups" in {
        val result = reformat(Seq(
          "GAUGE|system.cpu|os.CpuMonitor.record",
          "COUNTER|system.cpu|os.CpuMonitor.count",
          "GAUGE|system.memory|os.MemMonitor.record",
          "COUNTER|system.memory|os.MemMonitor.count"
        ))
        val sectionLines = result.dropWhile(_ != "# Records").tail
        sectionLines(2) mustBe ""
        sectionLines(3) must include("MemMonitor")
      }
    }
  }
}
