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

private[columnar] object ColumnarFormatter {

  private case class Row(cols: IndexedSeq[String])

  // ── Section detection ──────────────────────────────────────────────────────

  /** The catch-all is the first section with no prefixes at all; falls back to last. */
  private def catchAll(sections: Seq[ColumnarSection]): ColumnarSection =
    sections
      .find(s => s.primaryPrefixes.isEmpty && s.secondaryPrefixes.isEmpty)
      .getOrElse(sections.last)

  private def findSection(
    row:          Row,
    sections:     Seq[ColumnarSection],
    primaryCol:   Int,
    secondaryCol: Int
  ): ColumnarSection = {
    val primary   = row.cols(primaryCol)
    val secondary = row.cols(secondaryCol)

    // Longest matching primary prefix wins — specificity is implicit in prefix length
    val byPrimary =
      sections
        .flatMap(s => s.primaryPrefixes.filter(primary.startsWith(_)).map(p => p.length -> s))
        .sortBy(-_._1)
        .headOption
        .map(_._2)

    // Secondary prefix as fallback (e.g. scaffold rows with non-standard primaries)
    lazy val bySecondary =
      sections.find(s => s.secondaryPrefixes.exists(secondary.startsWith(_)))

    byPrimary.orElse(bySecondary).getOrElse(catchAll(sections))
  }

  // ── Subgroup grouping ──────────────────────────────────────────────────────

  private def groupBySubkey(rows: Seq[Row], subkeyFn: Row => String): Seq[Seq[Row]] =
    rows.foldLeft(Vector.empty[Vector[Row]]) { (acc, r) =>
      val key = subkeyFn(r)
      acc.lastOption match {
        case Some(last) if subkeyFn(last.last) == key =>
          acc.dropRight(1) :+ (last :+ r)
        case _ =>
          acc :+ Vector(r)
      }
    }

  // ── Section formatting ─────────────────────────────────────────────────────

  private def formatSection(
    spec:            ColumnarSection,
    rows:            Seq[Row],
    globalLineLimit: Int,
    subkeyFn:        Row => String,
    delimiter:       String
  ): Seq[String] = {
    if (rows.isEmpty) return Nil

    val limit             = spec.lineLimit.getOrElse(globalLineLimit)
    val sectionMaxCol0Len = rows.map(_.cols(0).length).max
    val sectionMaxCol1Len = rows.map(_.cols(1).length).max
    val sectionMaxCol2Len = rows.map(_.cols(2).length).max
    val worstCaseLine     = sectionMaxCol0Len + delimiter.length + sectionMaxCol1Len + delimiter.length + sectionMaxCol2Len
    val useSubgroupAlign  = worstCaseLine > limit

    def fmt(r: Row, col1Len: Int): String = {
      val c0 = r.cols(0).padTo(sectionMaxCol0Len, ' ')
      val c1 = r.cols(1).padTo(col1Len, ' ')
      s"$c0$delimiter$c1$delimiter${r.cols(2)}"
    }

    val groups = groupBySubkey(rows, subkeyFn)
    val lines = groups.flatMap { group =>
      val col1Len = if (useSubgroupAlign) group.map(_.cols(1).length).max else sectionMaxCol1Len
      group.map(fmt(_, col1Len)) :+ ""
    }.dropRight(1)

    spec.header +: lines
  }

  // ── Internal reformat ─────────────────────────────────────────────────────

  private def reformat(
    rows:         Seq[Row],
    sections:     Seq[ColumnarSection],
    lineLimit:    Int,
    fileHeader:   String,
    primaryCol:   Int,
    secondaryCol: Int,
    dedupeKey:    Row => Any,
    subkeyFn:     Row => String,
    delimiter:    String
  ): Seq[String] = {
    // Deduplicate by key, preserving first occurrence
    val seen   = collection.mutable.Set.empty[Any]
    val unique = rows.filter { r =>
      val key = dedupeKey(r)
      if (seen.contains(key)) false else { seen += key; true }
    }

    // Group into sections, preserving relative order within each section
    val bySection: Map[ColumnarSection, Vector[Row]] =
      unique.foldLeft(Map.empty[ColumnarSection, Vector[Row]]) { (acc, r) =>
        val s = findSection(r, sections, primaryCol, secondaryCol)
        acc + (s -> (acc.getOrElse(s, Vector.empty) :+ r))
      }

    val headerBlock: Seq[String]         = if (fileHeader.isEmpty) Nil else Seq(fileHeader)
    val sectionBlocks: List[Seq[String]] = sections.toList.flatMap { s =>
      bySection.get(s).map(rs => formatSection(s, rs, lineLimit, subkeyFn, delimiter))
    }

    val blocks: List[Seq[String]] =
      if (headerBlock.isEmpty) sectionBlocks else headerBlock :: sectionBlocks

    blocks.flatMap(b => b :+ "").dropRight(1)
  }

  // ── Public API ────────────────────────────────────────────────────────────

  def reformat(
    lines:      Seq[String],
    sections:   Seq[ColumnarSection],
    lineLimit:  Int,
    fileHeader: String,
    config:     ColumnarFormatterConfig
  ): Seq[String] =
    reformat(
      rows         = lines.flatMap(l => config.parse(l).map(Row(_))),
      sections     = sections,
      lineLimit    = lineLimit,
      fileHeader   = fileHeader,
      primaryCol   = config.primaryCol,
      secondaryCol = config.secondaryCol,
      dedupeKey    = row => config.dedupeKey(row.cols),
      subkeyFn     = row => config.subkeyFn(row.cols),
      delimiter    = config.delimiter
    )
}
