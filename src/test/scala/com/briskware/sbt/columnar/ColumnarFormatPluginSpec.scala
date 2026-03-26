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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sbt._
import sbt.io.IO

/** Tests for the logic executed by the `columnarFmt` task.
  *
  * The task body iterates over a [[Seq]] of [[ColumnarConfig]] entries; for each it resolves
  * files via [[ColumnarGlob]], reformats them with [[ColumnarFormatter]], and writes the result
  * back. This spec exercises that logic directly (without launching a real sbt session) to
  * verify multi-config behaviour introduced when `columnarFmtConfig` was changed from a single
  * value to a sequence.
  */
class ColumnarFormatPluginSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  private var tmp: File = _

  override def beforeEach(): Unit = { tmp = IO.createTemporaryDirectory }
  override def afterEach(): Unit  = { IO.delete(tmp) }

  /** Replicates the plugin task body so we can exercise it without an sbt session. */
  private def runTask(baseDir: File, configs: Seq[ColumnarConfig]): Unit =
    configs.foreach { cfg =>
      ColumnarGlob.resolve(baseDir, cfg.fileGlob).foreach { file =>
        IO.writeLines(file,
          ColumnarFormatter.reformat(
            IO.readLines(file),
            cfg.sections,
            cfg.lineLimit,
            cfg.fileHeader,
            cfg.formatterConfig
          )
        )
      }
    }

  /** Replicates the core detection/exception logic of the columnarFmtCheck task; throws MessageOnlyException if any file is unformatted. */
  private def runCheckTask(baseDir: File, configs: Seq[ColumnarConfig]): Unit = {
    val unformatted = configs.flatMap { cfg =>
      val files = ColumnarGlob.resolve(baseDir, cfg.fileGlob)
      IO.withTemporaryFile("sbt-columnar-format", "tmp") { tmp =>
        files.filter { file =>
          val currentBytes = IO.readBytes(file)
          IO.writeLines(tmp, ColumnarFormatter.reformat(IO.readLines(file), cfg.sections, cfg.lineLimit, cfg.fileHeader, cfg.formatterConfig))
          !java.util.Arrays.equals(currentBytes, IO.readBytes(tmp))
        }
      }
    }
    val unformattedUnique = unformatted.map(_.getCanonicalFile).distinct
    if (unformattedUnique.nonEmpty)
      throw new MessageOnlyException(
        s"[sbt-columnar-format] ${unformattedUnique.size} file(s) are not formatted. Run columnarFmt to fix."
      )
  }

  // ── Fixtures ──────────────────────────────────────────────────────────────

  // playRoutes uses primaryCol = 1 (the URL path), so prefixes must match paths, not methods
  private val routesSections = Seq(
    ColumnarSection("# Ping",  primaryPrefixes = Seq("/ping")),
    ColumnarSection("# Login", primaryPrefixes = Seq("/login")),
    ColumnarSection("# Misc")
  )

  private val csvSections = Seq(
    ColumnarSection("# OS",  primaryPrefixes = Seq("system.")),
    ColumnarSection("# Misc")
  )

  private def writeRoutes(file: File): Unit =
    IO.write(file,
      "GET  /ping  controllers.HealthController.ping\n" +
      "POST /login controllers.AuthController.login\n"
    )

  private def writeCsv(file: File): Unit =
    IO.write(file,
      "GAUGE,system.cpu,os.CpuMonitor.record\n" +
      "COUNTER,system.memory,os.MemMonitor.count\n"
    )

  // ── Single config ─────────────────────────────────────────────────────────

  "columnarFmt task" when {

    "given a single config" should {

      "format every file matched by the glob" in {
        IO.createDirectory(tmp / "a")
        IO.createDirectory(tmp / "b")
        writeRoutes(tmp / "a" / "a.routes")
        writeRoutes(tmp / "b" / "b.routes")

        runTask(tmp, Seq(ColumnarConfig(
          fileGlob        = "**/*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )))

        IO.readLines(tmp / "a" / "a.routes") must contain("# Ping")
        IO.readLines(tmp / "b" / "b.routes") must contain("# Ping")
      }

      "leave files that do not match the glob untouched" in {
        writeRoutes(tmp / "app.routes")
        val originalCsv = "GAUGE,system.cpu,os.CpuMonitor.record"
        IO.write(tmp / "data.csv", originalCsv)

        runTask(tmp, Seq(ColumnarConfig(
          fileGlob        = "*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )))

        IO.read(tmp / "data.csv").trim mustBe originalCsv
      }

      "write a file header when fileHeader is set" in {
        writeRoutes(tmp / "app.routes")

        runTask(tmp, Seq(ColumnarConfig(
          fileGlob        = "*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes,
          fileHeader      = "# Do not edit"
        )))

        IO.readLines(tmp / "app.routes").head mustBe "# Do not edit"
      }

      "complete without error when no files match the glob" in {
        noException should be thrownBy runTask(tmp, Seq(ColumnarConfig(
          fileGlob        = "**/*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )))
      }
    }

    // ── Multiple configs ─────────────────────────────────────────────────────

    "given multiple configs" should {

      "apply each config independently to its own matching files" in {
        IO.createDirectory(tmp / "conf")
        IO.createDirectory(tmp / "data")
        writeRoutes(tmp / "conf" / "app.routes")
        writeCsv(tmp / "data" / "metrics.csv")

        runTask(tmp, Seq(
          ColumnarConfig(
            fileGlob        = "**/*.routes",
            sections        = routesSections,
            formatterConfig = ColumnarFormatterConfig.playRoutes
          ),
          ColumnarConfig(
            fileGlob        = "**/*.csv",
            sections        = csvSections,
            formatterConfig = ColumnarFormatterConfig.csv
          )
        ))

        IO.readLines(tmp / "conf" / "app.routes") must contain("# Ping")
        IO.readLines(tmp / "data" / "metrics.csv") must contain("# OS")
      }

      "use a different formatter for each file type" in {
        writeRoutes(tmp / "app.routes")
        writeCsv(tmp / "metrics.csv")

        runTask(tmp, Seq(
          ColumnarConfig(
            fileGlob        = "*.routes",
            sections        = routesSections,
            formatterConfig = ColumnarFormatterConfig.playRoutes
          ),
          ColumnarConfig(
            fileGlob        = "*.csv",
            sections        = csvSections,
            formatterConfig = ColumnarFormatterConfig.csv
          )
        ))

        // Routes file uses space-delimited output (no commas in data lines)
        val routeLines = IO.readLines(tmp / "app.routes").filterNot(l => l.startsWith("#") || l.isEmpty)
        all(routeLines) must not include ","

        // CSV file uses comma-delimited output
        val csvLines = IO.readLines(tmp / "metrics.csv").filterNot(l => l.startsWith("#") || l.isEmpty)
        all(csvLines) must include(",")
      }

      "not cross-contaminate files between configs" in {
        writeRoutes(tmp / "app.routes")
        writeCsv(tmp / "metrics.csv")

        runTask(tmp, Seq(
          ColumnarConfig(
            fileGlob        = "*.routes",
            sections        = routesSections,
            formatterConfig = ColumnarFormatterConfig.playRoutes
          ),
          ColumnarConfig(
            fileGlob        = "*.csv",
            sections        = csvSections,
            formatterConfig = ColumnarFormatterConfig.csv
          )
        ))

        // CSV section headers must not appear in the routes file, and vice versa
        IO.readLines(tmp / "app.routes") must not contain "# OS"
        IO.readLines(tmp / "metrics.csv") must not contain "# Ping"
      }

      "apply per-config fileHeaders independently" in {
        writeRoutes(tmp / "app.routes")
        writeCsv(tmp / "metrics.csv")

        runTask(tmp, Seq(
          ColumnarConfig(
            fileGlob        = "*.routes",
            sections        = routesSections,
            formatterConfig = ColumnarFormatterConfig.playRoutes,
            fileHeader      = "# routes header"
          ),
          ColumnarConfig(
            fileGlob        = "*.csv",
            sections        = csvSections,
            formatterConfig = ColumnarFormatterConfig.csv,
            fileHeader      = "# csv header"
          )
        ))

        IO.readLines(tmp / "app.routes").head mustBe "# routes header"
        IO.readLines(tmp / "metrics.csv").head mustBe "# csv header"
      }

      "complete without error when all configs match no files" in {
        noException should be thrownBy runTask(tmp, Seq(
          ColumnarConfig(fileGlob = "**/*.routes", sections = routesSections,
                         formatterConfig = ColumnarFormatterConfig.playRoutes),
          ColumnarConfig(fileGlob = "**/*.csv",    sections = csvSections,
                         formatterConfig = ColumnarFormatterConfig.csv)
        ))
      }
    }

    // ── Empty config list ────────────────────────────────────────────────────

    "given an empty config list" should {

      "complete without error and not modify any files" in {
        val content = "GAUGE,system.cpu,os.CpuMonitor.record"
        IO.write(tmp / "metrics.csv", content)

        noException should be thrownBy runTask(tmp, Seq.empty)

        IO.read(tmp / "metrics.csv").trim mustBe content
      }
    }
  }

  // ── columnarFmtCheck task ──────────────────────────────────────────────────

  "columnarFmtCheck task" when {

    "given a single config" should {

      "succeed when the file is already formatted" in {
        writeRoutes(tmp / "app.routes")
        val cfg = ColumnarConfig(
          fileGlob        = "*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )
        // Format first so the file is in the canonical state
        runTask(tmp, Seq(cfg))

        noException should be thrownBy runCheckTask(tmp, Seq(cfg))
      }

      "fail when the file is not formatted" in {
        writeRoutes(tmp / "app.routes")
        val cfg = ColumnarConfig(
          fileGlob        = "*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )
        // Deliberately skip runTask so the file is unformatted

        a[MessageOnlyException] should be thrownBy runCheckTask(tmp, Seq(cfg))
      }

      "succeed when no files match the glob" in {
        noException should be thrownBy runCheckTask(tmp, Seq(ColumnarConfig(
          fileGlob        = "**/*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )))
      }

      "succeed when all files in a multi-file glob are already formatted" in {
        IO.createDirectory(tmp / "a")
        IO.createDirectory(tmp / "b")
        writeRoutes(tmp / "a" / "a.routes")
        writeRoutes(tmp / "b" / "b.routes")
        val cfg = ColumnarConfig(
          fileGlob        = "**/*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )
        runTask(tmp, Seq(cfg))

        noException should be thrownBy runCheckTask(tmp, Seq(cfg))
      }

      "fail when the only difference is a missing trailing newline" in {
        writeRoutes(tmp / "app.routes")
        val cfg = ColumnarConfig(
          fileGlob        = "*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )
        // First format the file into its canonical state
        runTask(tmp, Seq(cfg))

        // Strip any trailing newline characters so the logical lines are unchanged
        // but the on-disk bytes differ from what IO.writeLines would produce
        val file      = tmp / "app.routes"
        val formatted = IO.read(file)
        val withoutTrailingNewline =
          formatted.reverse.dropWhile(ch => ch == '\n' || ch == '\r').reverse
        IO.write(file, withoutTrailingNewline)

        a[MessageOnlyException] should be thrownBy runCheckTask(tmp, Seq(cfg))
      }

      "fail when at least one file in a multi-file glob is not formatted" in {
        IO.createDirectory(tmp / "a")
        IO.createDirectory(tmp / "b")
        writeRoutes(tmp / "a" / "a.routes")
        writeRoutes(tmp / "b" / "b.routes")
        val cfg = ColumnarConfig(
          fileGlob        = "**/*.routes",
          sections        = routesSections,
          formatterConfig = ColumnarFormatterConfig.playRoutes
        )
        // Format only one of the two files
        runTask(tmp, Seq(cfg.copy(fileGlob = "a/a.routes")))

        a[MessageOnlyException] should be thrownBy runCheckTask(tmp, Seq(cfg))
      }
    }

    "given multiple configs" should {

      "succeed when all files across all configs are already formatted" in {
        writeRoutes(tmp / "app.routes")
        writeCsv(tmp / "metrics.csv")
        val configs = Seq(
          ColumnarConfig(fileGlob = "*.routes", sections = routesSections,
                         formatterConfig = ColumnarFormatterConfig.playRoutes),
          ColumnarConfig(fileGlob = "*.csv",    sections = csvSections,
                         formatterConfig = ColumnarFormatterConfig.csv)
        )
        runTask(tmp, configs)

        noException should be thrownBy runCheckTask(tmp, configs)
      }

      "fail when a file matched by the second config is not formatted" in {
        writeRoutes(tmp / "app.routes")
        writeCsv(tmp / "metrics.csv")
        val routesCfg = ColumnarConfig(fileGlob = "*.routes", sections = routesSections,
                                        formatterConfig = ColumnarFormatterConfig.playRoutes)
        val csvCfg    = ColumnarConfig(fileGlob = "*.csv",    sections = csvSections,
                                        formatterConfig = ColumnarFormatterConfig.csv)
        // Only format the routes file; leave the CSV unformatted
        runTask(tmp, Seq(routesCfg))

        a[MessageOnlyException] should be thrownBy runCheckTask(tmp, Seq(routesCfg, csvCfg))
      }

      "succeed when all configs match no files" in {
        noException should be thrownBy runCheckTask(tmp, Seq(
          ColumnarConfig(fileGlob = "**/*.routes", sections = routesSections,
                         formatterConfig = ColumnarFormatterConfig.playRoutes),
          ColumnarConfig(fileGlob = "**/*.csv",    sections = csvSections,
                         formatterConfig = ColumnarFormatterConfig.csv)
        ))
      }
    }

    "given an empty config list" should {

      "succeed without error" in {
        noException should be thrownBy runCheckTask(tmp, Seq.empty)
      }
    }
  }
}