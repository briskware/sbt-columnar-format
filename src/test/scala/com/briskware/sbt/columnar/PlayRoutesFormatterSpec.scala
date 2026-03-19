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

class PlayRoutesFormatterSpec extends AnyWordSpec with Matchers {

  // ── Test helpers ──────────────────────────────────────────────────────────

  private def reformat(
    input:      String,
    sections:   Seq[ColumnarSection] = Seq(ColumnarSection("# Routes")),
    lineLimit:  Int                  = 999,
    fileHeader: String               = ""
  ): Seq[String] =
    ColumnarFormatter.reformat(input.linesIterator.toSeq, sections, lineLimit, fileHeader,
      ColumnarFormatterConfig.playRoutes)

  /** Find the last section header before the line containing a given substring. */
  private def sectionBefore(result: Seq[String], containing: String): String =
    result
      .take(result.indexWhere(_.contains(containing)))
      .filter(_.startsWith("#"))
      .last

  // ── Fictional "Acme" sections used for section-grouping tests ─────────────
  //
  // Simulates a multi-area app with:
  //   Core       – catch-all (no prefixes)
  //   System     – error and auth paths
  //   Widgets    – generic item paths
  //   Gadgets    – a sub-area of items with its own path AND controller prefix
  //   Gizmos     – another sub-area of items with its own path AND controller prefix
  //
  // This mirrors the structural patterns exercised by the tests without
  // referencing any real domain.

  private val acmeSections: Seq[ColumnarSection] = Seq(
    ColumnarSection("# Core"),
    ColumnarSection("# System",
      primaryPrefixes = Seq("/error/", "/login/", "/forbidden/")),
    ColumnarSection("# Widgets",
      primaryPrefixes = Seq("/items/")),
    ColumnarSection("# Gadgets",
      primaryPrefixes   = Seq("/items/gadget/"),
      secondaryPrefixes = Seq("controllers.gadget.")),
    ColumnarSection("# Gizmos",
      primaryPrefixes   = Seq("/items/gizmo/"),
      secondaryPrefixes = Seq("controllers.gizmo."))
  )

  // ── File header ───────────────────────────────────────────────────────────

  "RoutesFormatter" when {

    "given a fileHeader" should {

      "write it as the first line" in {
        val result = reformat(
          "GET   /      controllers.HomeController.onPageLoad()",
          fileHeader = "# my routes"
        )
        result.head mustBe "# my routes"
      }

      "omit the header line when fileHeader is empty" in {
        val result = reformat(
          "  GET / controllers.HomeController.onPageLoad()",
          fileHeader = ""
        )
        result.head must not be empty
        result.head must startWith("# ")
        result.head must not be "# my routes"
      }
    }

    // ── Section grouping ──────────────────────────────────────────────────

    "grouping routes into sections" should {

      "place delegation lines in the catch-all section" in {
        val result = reformat(
          "->    /vendor    vendor.Routes",
          sections = acmeSections
        )
        sectionBefore(result, "vendor.Routes") mustBe "# Core"
      }

      "place routes in the section whose path prefix matches" in {
        val result = reformat(
          """GET /items/gadget/name controllers.gadget.GadgetNameController.onPageLoad()
            |    GET   /items/gizmo/name     controllers.gizmo.GizmoNameController.onPageLoad()
            |  GET      /items/name         controllers.widgets.WidgetNameController.onPageLoad()
            |GET  /error/503  controllers.SystemController.onPageLoad()
            |   GET /    controllers.HomeController.onPageLoad()""".stripMargin,
          sections = acmeSections
        )
        sectionBefore(result, "GadgetNameController") mustBe "# Gadgets"
        sectionBefore(result, "GizmoNameController")  mustBe "# Gizmos"
        sectionBefore(result, "WidgetNameController") mustBe "# Widgets"
        sectionBefore(result, "SystemController")     mustBe "# System"
        sectionBefore(result, "HomeController")       mustBe "# Core"
      }

      "use the longer prefix when a route matches multiple sections (specificity)" in {
        // /items/gadget/x matches both /items/ (Widgets) and /items/gadget/ (Gadgets)
        // Gadgets should win because its prefix is longer
        val result = reformat(
          "GET /items/gadget/x controllers.gadget.GadgetXController.onPageLoad()",
          sections = acmeSections
        )
        sectionBefore(result, "GadgetXController") mustBe "# Gadgets"
      }

      "use the controller prefix as a fallback when the path does not match any section" in {
        val result = reformat(
          "  GET  /gadgetSummary controllers.gadget.GadgetSummaryController.onPageLoad()",
          sections = acmeSections
        )
        sectionBefore(result, "GadgetSummaryController") mustBe "# Gadgets"
      }

      "output sections in the order they are defined" in {
        val result = reformat(
          """GET /items/gizmo/name controllers.gizmo.GizmoNameController.onPageLoad()
            |      GET /items/gadget/name  controllers.gadget.GadgetNameController.onPageLoad()
            |  GET  /items/name controllers.widgets.WidgetNameController.onPageLoad()
            |GET    /error/503        controllers.SystemController.onPageLoad()
            | GET / controllers.HomeController.onPageLoad()""".stripMargin,
          sections = acmeSections
        )
        val headers = result.filter(_.startsWith("#"))
        headers mustBe Seq("# Core", "# System", "# Widgets", "# Gadgets", "# Gizmos")
      }

      "omit sections that have no matching routes" in {
        val result = reformat(
          "GET /items/gadget/name controllers.gadget.GadgetNameController.onPageLoad()",
          sections = acmeSections
        )
        result must not contain "# Gizmos"
        result must not contain "# Widgets"
      }
    }

    // ── Column alignment ──────────────────────────────────────────────────

    "aligning columns" should {

      "align all controllers to the same column within a section when within the line limit" in {
        val result = reformat(
          """GET /items/gadget/short controllers.gadget.ShortController.onPageLoad()
            |POST /items/gadget/short controllers.gadget.ShortController.onSubmit()
            |GET /items/gadget/a-much-longer-path controllers.gadget.LongerController.onPageLoad()
            |POST /items/gadget/a-much-longer-path controllers.gadget.LongerController.onSubmit()""".stripMargin
        )
        val routeLines = result.filter(l => l.startsWith("GET") || l.startsWith("POST"))
        routeLines.map(_.indexOf("controllers")).distinct must have size 1
      }

      "use per-subgroup alignment when the worst-case line exceeds the line limit" in {
        val result = reformat(
          """GET /items/gadget/short controllers.gadget.ShortController.onPageLoad()
            |POST /items/gadget/short controllers.gadget.ShortController.onSubmit()
            |GET /items/gadget/a-much-longer-path controllers.gadget.LongerController.onPageLoad()
            |POST /items/gadget/a-much-longer-path controllers.gadget.LongerController.onSubmit()""".stripMargin,
          lineLimit = 1
        )
        val routeLines = result.filter(l => l.startsWith("GET") || l.startsWith("POST"))
        routeLines.map(_.indexOf("controllers")).distinct.size must be > 1
      }

      "use the section's own lineLimit when set, overriding the global limit" in {
        val sectionWithOwnLimit = Seq(
          ColumnarSection("# Short", primaryPrefixes = Seq("/short/"), lineLimit = Some(999)),
          ColumnarSection("# Long",  primaryPrefixes = Seq("/long/"),  lineLimit = Some(1))
        )
        val result = reformat(
          """GET /short/a controllers.ShortController.onPageLoad()
            |POST /short/a controllers.ShortController.onSubmit()
            |GET /short/longer controllers.ShortController.onPageLoad(edit: Boolean = false)
            |POST /short/longer controllers.ShortController.onSubmit(edit: Boolean = false)
            |GET /long/a controllers.LongAController.onPageLoad()
            |POST /long/a controllers.LongAController.onSubmit()
            |GET /long/longer controllers.LongBController.onPageLoad()
            |POST /long/longer controllers.LongBController.onSubmit()""".stripMargin,
          sections = sectionWithOwnLimit  // global lineLimit stays at 999 (default) — section overrides it
        )
        val shortLines = result.dropWhile(_ != "# Short").tail.takeWhile(_ != "# Long").filter(l => l.startsWith("GET") || l.startsWith("POST"))
        val longLines  = result.dropWhile(_ != "# Long").tail.filter(l => l.startsWith("GET") || l.startsWith("POST"))

        // Short section: global limit 999, section limit 999 → section-wide alignment
        shortLines.map(_.indexOf("controllers")).distinct must have size 1
        // Long section: section limit 1 → per-subgroup alignment
        longLines.map(_.indexOf("controllers")).distinct.size must be > 1
      }
    }

    // ── Controller subgroup blank lines ────────────────────────────────────

    "inserting blank lines between subgroups" should {

      "insert exactly one blank line between two different controller subgroups" in {
        val result = reformat(
          """GET /items/alpha controllers.AlphaController.onPageLoad()
            |POST /items/alpha controllers.AlphaController.onSubmit()
            |GET /items/beta controllers.BetaController.onPageLoad()
            |POST /items/beta controllers.BetaController.onSubmit()""".stripMargin
        )
        val sectionLines = result.dropWhile(_ != "# Routes").tail
        sectionLines(2) mustBe ""
        sectionLines(3) must include("BetaController")
      }

      "not insert blank lines within a single controller subgroup" in {
        val result = reformat(
          """GET /items/alpha controllers.AlphaController.onPageLoad()
            |POST /items/alpha controllers.AlphaController.onSubmit()
            |GET /items/change-alpha controllers.AlphaController.onPageLoad(edit: Boolean = false)
            |POST /items/change-alpha controllers.AlphaController.onSubmit(edit: Boolean = false)""".stripMargin
        )
        val sectionLines = result.dropWhile(_ != "# Routes").tail
        sectionLines.filter(_.isEmpty) must be(empty)
      }
    }

    // ── Deduplication ─────────────────────────────────────────────────────

    "deduplicating routes" should {

      "remove duplicate (method, path) pairs keeping the first occurrence" in {
        val result = reformat(
          """GET /items/alpha controllers.AlphaController.onPageLoad()
            |GET /items/alpha controllers.AlphaController.onPageLoad()""".stripMargin
        )
        result.count(_.contains("/items/alpha")) mustBe 1
      }

      "keep routes with the same path but different methods" in {
        val result = reformat(
          """GET /items/alpha controllers.AlphaController.onPageLoad()
            |POST /items/alpha controllers.AlphaController.onSubmit()""".stripMargin
        )
        result.count(_.contains("/items/alpha")) mustBe 2
      }
    }

    // ── Input hygiene ─────────────────────────────────────────────────────

    "handling input comments and blank lines" should {

      "strip existing comments (section headers are regenerated by the formatter)" in {
        val result = reformat(
          """# some existing comment
            |GET /items/alpha controllers.AlphaController.onPageLoad()""".stripMargin
        )
        result.count(_ == "# some existing comment") mustBe 0
      }

      "strip blank lines from the input (structure is regenerated)" in {
        val result = reformat(
          """
            |GET /items/alpha controllers.AlphaController.onPageLoad()
            |
            |""".stripMargin
        )
        // Only formatter-generated blank lines should exist (between sections / subgroups)
        result.count(_.isEmpty) must be < 3
      }
    }

    // ── README example ────────────────────────────────────────────────────

    "formatting the README example" should {

      "produce the exact output shown in the README" in {
        val result = reformat(
          """GET /ping controllers.HealthController.ping
            |GET /admin/users controllers.admin.UserController.list
            |POST /admin/users controllers.admin.UserController.create
            |GET /public/home controllers.HomeController.index""".stripMargin,
          sections = Seq(
            ColumnarSection("# Infrastructure",
              primaryPrefixes   = Seq("/ping", "/assets"),
              secondaryPrefixes = Seq("controllers.Assets")),
            ColumnarSection("# Public",
              primaryPrefixes   = Seq("/public")),
            ColumnarSection("# Admin",
              primaryPrefixes   = Seq("/admin"),
              lineLimit         = Some(160)),
            ColumnarSection("# Misc")
          ),
          fileHeader = "# Auto-formatted — do not edit by hand"
        )
        result mustBe
          """# Auto-formatted — do not edit by hand
            |
            |# Infrastructure
            |GET  /ping  controllers.HealthController.ping
            |
            |# Public
            |GET  /public/home  controllers.HomeController.index
            |
            |# Admin
            |GET   /admin/users  controllers.admin.UserController.list
            |POST  /admin/users  controllers.admin.UserController.create""".stripMargin.linesIterator.toSeq
      }
    }

    // ── Section separators ────────────────────────────────────────────────

    "separating sections" should {

      "place exactly one blank line between adjacent sections" in {
        val result = reformat(
          """GET /error/503 controllers.SystemController.onPageLoad()
            |GET /items/gadget/x controllers.gadget.GadgetXController.onPageLoad()""".stripMargin,
          sections = acmeSections
        )
        val gadgetsIdx = result.indexOf("# Gadgets")
        result(gadgetsIdx - 1) mustBe ""
        result(gadgetsIdx - 2) must not be ""
      }

      "not emit a trailing blank line at end of file" in {
        val result = reformat(
          "GET /items/alpha controllers.AlphaController.onPageLoad()"
        )
        result.last must not be ""
      }
    }
  }
}
