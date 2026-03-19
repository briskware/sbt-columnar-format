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

import org.scalatest.{BeforeAndAfterAll}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sbt._
import sbt.io.IO

/** Tests for [[ColumnarGlob.resolve]] and multi-file formatting via a glob.
  *
  * File tree used across the glob-resolution tests:
  * {{{
  *   base/
  *     conf/
  *       app.routes
  *       other.routes
  *     module/
  *       conf/
  *         module.routes
  *     data/
  *       records.csv
  *       notes.txt
  * }}}
  */
class ColumnarGlobSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private var base: File = _

  override def beforeAll(): Unit = {
    base = IO.createTemporaryDirectory
    IO.createDirectory(base / "conf")
    IO.createDirectory(base / "module" / "conf")
    IO.createDirectory(base / "data")
    IO.write(base / "conf" / "app.routes",              "GET / controllers.HomeController.index()")
    IO.write(base / "conf" / "other.routes",            "GET /other controllers.OtherController.index()")
    IO.write(base / "module" / "conf" / "module.routes","GET /module controllers.ModuleController.index()")
    IO.write(base / "data" / "records.csv",             "type,path,handler")
    IO.write(base / "data" / "notes.txt",               "some notes")
  }

  override def afterAll(): Unit = IO.delete(base)

  // ── Exact path ────────────────────────────────────────────────────────────

  "ColumnarGlob.resolve" when {

    "given an exact file path" should {

      "return the single matching file" in {
        ColumnarGlob.resolve(base, "conf/app.routes") mustBe
          Seq(base / "conf" / "app.routes")
      }

      "return empty when the file does not exist" in {
        ColumnarGlob.resolve(base, "conf/nonexistent.routes") mustBe empty
      }
    }

    // ── Single-level wildcard (*) ──────────────────────────────────────────

    "given a non-recursive wildcard (single directory)" should {

      "return all matching files in that directory" in {
        val result = ColumnarGlob.resolve(base, "conf/*.routes")
        result must have size 2
        result.map(_.getName) must contain allOf("app.routes", "other.routes")
      }

      "not include files from subdirectories" in {
        ColumnarGlob.resolve(base, "conf/*.routes").map(_.getName) must not contain "module.routes"
      }

      "not include files with a non-matching extension" in {
        ColumnarGlob.resolve(base, "data/*.routes") mustBe empty
      }

      "return empty when the directory does not exist" in {
        ColumnarGlob.resolve(base, "nonexistent/*.routes") mustBe empty
      }
    }

    // ── Recursive wildcard (**) ────────────────────────────────────────────

    "given a recursive wildcard (**)" should {

      "return all matching files in the entire tree" in {
        val result = ColumnarGlob.resolve(base, "**/*.routes")
        result must have size 3
        result.map(_.getName) must contain allOf("app.routes", "other.routes", "module.routes")
      }

      "not include files with a non-matching extension" in {
        val result = ColumnarGlob.resolve(base, "**/*.routes")
        result.map(_.getName) must not contain "records.csv"
        result.map(_.getName) must not contain "notes.txt"
      }

      "scope the recursion to the fixed path prefix" in {
        // conf/**/*.routes should only find files under conf/, not module/conf/
        val result = ColumnarGlob.resolve(base, "conf/**/*.routes")
        result must have size 2
        result.map(_.getName) must contain allOf("app.routes", "other.routes")
        result.map(_.getName) must not contain "module.routes"
      }
    }

    // ── Multi-file formatting ─────────────────────────────────────────────

    "formatting multiple files matched by a glob" should {

      "format each matched file independently" in {
        val tmp = IO.createTemporaryDirectory
        try {
          IO.createDirectory(tmp / "a")
          IO.createDirectory(tmp / "b")
          IO.write(tmp / "a" / "a.routes",
            "GET  /items/alpha  controllers.AlphaController.onPageLoad()\n" +
            "POST /items/alpha  controllers.AlphaController.onSubmit()")
          IO.write(tmp / "b" / "b.routes",
            "GET  /items/beta  controllers.BetaController.onPageLoad()")

          val files = ColumnarGlob.resolve(tmp, "**/*.routes")
          files must have size 2

          files.foreach { file =>
            IO.writeLines(file,
              ColumnarFormatter.reformat(
                IO.readLines(file),
                Seq(ColumnarSection("# Routes")),
                120,
                "",
                ColumnarFormatterConfig.playRoutes
              )
            )
          }

          IO.readLines(tmp / "a" / "a.routes") must contain("# Routes")
          IO.readLines(tmp / "b" / "b.routes") must contain("# Routes")
        } finally IO.delete(tmp)
      }

      "leave files that do not match the glob untouched" in {
        val tmp = IO.createTemporaryDirectory
        try {
          IO.write(tmp / "app.routes", "GET / controllers.HomeController.index()")
          IO.write(tmp / "data.csv",   "col1,col2,col3")

          val files = ColumnarGlob.resolve(tmp, "*.routes")
          files.map(_.getName) mustBe Seq("app.routes")
        } finally IO.delete(tmp)
      }
    }
  }
}
