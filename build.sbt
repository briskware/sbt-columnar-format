ThisBuild / organization := "com.briskware"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / startYear    := Some(2026)
ThisBuild / licenses     := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / description  := "sbt plugin for formatting columnar text files: aligns columns, groups rows into sections, deduplicates"
ThisBuild / homepage     := Some(url("https://github.com/briskware/sbt-columnar-format"))

lazy val root = (project in file("."))
  .settings(
    name             := "sbt-columnar-format",
    sbtPlugin        := true,
    scalaVersion     := "2.12.20",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
  )