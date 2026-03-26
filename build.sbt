ThisBuild / organization := "com.briskware"
ThisBuild / version      := "0.1.1-SNAPSHOT"
ThisBuild / startYear    := Some(2026)
ThisBuild / licenses     := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / description  := "sbt plugin for formatting columnar text files: aligns columns, groups rows into sections, deduplicates"
ThisBuild / homepage     := Some(url("https://github.com/briskware/sbt-columnar-format"))

ThisBuild / developers := List(
  Developer(
    id    = "briskware",
    name  = "Briskware Ltd",
    email = "developer@briskware.com",
    url   = url("https://github.com/briskware")
  ),
  Developer(
    id    = "szaniszlo",
    name  = "Stefan Szaniszlo",
    email = "stefan@briskware.com",
    url   = url("https://github.com/szaniszlo")
  ),
)

ThisBuild / scmInfo := Some(ScmInfo(
  url("https://github.com/briskware/sbt-columnar-format"),
  "scm:git@github.com:briskware/sbt-columnar-format.git"
))

ThisBuild / pomIncludeRepository   := { _ => false }
ThisBuild / publishMavenStyle      := true
ThisBuild / sonatypeCredentialHost := "central.sonatype.com"

import ReleaseTransformations._

lazy val root = (project in file("."))
  .settings(
    name             := "sbt-columnar-format",
    sbtPlugin        := true,
    scalaVersion     := "2.12.20",
    publishTo        := sonatypePublishToBundle.value,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test,

    // Coverage (enable via: sbt coverage test coverageReport)
    coverageMinimumStmtTotal   := 92,
    coverageMinimumBranchTotal := 86,
    coverageFailOnMinimum      := true,
    coverageHighlighting       := true,

    // Suppress the plain (non-cross-versioned) artifacts — Maven Central only accepts the sbt-plugin cross-versioned ones
    packagedArtifacts := packagedArtifacts.value.filter { case (art, _) => art.name != name.value },

    // Release
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommand("publishSigned"),
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
