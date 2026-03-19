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

// Resolves a glob pattern (relative to a base directory) to a sorted list of matching files.
//
// Supported syntax (forward-slash-separated, relative to `base`):
//   - No wildcards : exact relative file path, e.g. "conf/app.routes"
//   - Single * only: single-directory wildcard,  e.g. "conf/*.routes"
//   - ** then name : recursive wildcard,          e.g. "conf/**/*.routes" or "**/*.routes"
private[columnar] object ColumnarGlob {

  def resolve(base: File, glob: String): Seq[File] =
    if (!glob.contains('*'))
      exactFile(base, glob)
    else {
      val segments              = glob.split("/").toList
      val (fixed, wildcardPart) = segments.span(!_.contains('*'))
      val dir                   = fixed.foldLeft(base)(_ / _)
      if (!dir.exists()) Seq.empty
      else wildcardPart match {
        case "**" :: filter :: Nil => (PathFinder(dir) ** filter).get.filter(_.isFile).toSeq.sortBy(_.getAbsolutePath)
        case filter        :: Nil  => (PathFinder(dir) *  filter).get.filter(_.isFile).toSeq.sortBy(_.getAbsolutePath)
        case _                     => (PathFinder(dir) ** wildcardPart.last).get.filter(_.isFile).toSeq.sortBy(_.getAbsolutePath)
      }
    }

  private def exactFile(base: File, path: String): Seq[File] = {
    val file = path.split("/").foldLeft(base)(_ / _)
    if (file.exists() && file.isFile) Seq(file) else Seq.empty
  }
}
