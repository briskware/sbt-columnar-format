# sbt-columnar-format

An sbt AutoPlugin that formats columnar text files: groups rows into configurable sections,
aligns columns, inserts blank lines between subgroups, and deduplicates entries.

Works with any whitespace-, comma-, tab-, or pipe-delimited file — just swap the config.

## Installation

In `project/plugins.sbt`:

```scala
addSbtPlugin("com.briskware" % "sbt-columnar-format" % "0.1.0-SNAPSHOT")
```

Run the formatter from sbt:

```
sbt columnarFmt
```

---

## Configuration

All settings are consolidated into a single `ColumnarConfig` value:

```scala
// build.sbt
columnarFmtConfig := ColumnarConfig(
  formatterConfig = ColumnarFormatterConfig.csv,
  fileGlob        = "**/*.csv",
  lineLimit       = 120,
  fileHeader      = "",
  sections        = Seq(ColumnarSection("# Default"))
)
```

| Field             | Type                      | Default                          |
|-------------------|---------------------------|----------------------------------|
| `formatterConfig` | `ColumnarFormatterConfig` | `ColumnarFormatterConfig.csv`    |
| `sections`        | `Seq[ColumnarSection]`    | `Seq(ColumnarSection("# Default"))` |
| `lineLimit`       | `Int`                     | `120`                            |
| `fileGlob`        | `String`                  | `"**/*.csv"`                     |
| `fileHeader`      | `String`                  | `""`                             |

`fileGlob` supports:
- exact path: `"conf/app.routes"`
- single-level wildcard: `"conf/*.txt"`
- recursive wildcard: `"**/*.csv"`

---

## Variants

### 1. CSV (default)

Comma-separated `col0,col1,col2`. Column 1 is used for section matching.

```scala
// build.sbt — these are the defaults, override only what you need
columnarFmtConfig := ColumnarConfig(
  formatterConfig = ColumnarFormatterConfig.csv,
  fileGlob        = "**/*.csv",
  sections        = Seq(
    ColumnarSection("# OS",  primaryPrefixes = Seq("system.")),
    ColumnarSection("# Web", primaryPrefixes = Seq("app.")),
    ColumnarSection("# Misc")
  )
)
```

**Input** (`metrics.csv`):
```
GAUGE,system.cpu,os.CpuMonitor.record
COUNTER,app.requests,web.RequestTracker.count
GAUGE,infra.disk,storage.DiskMonitor.record
```

**Output**:
```
# OS
GAUGE,system.cpu,os.CpuMonitor.record

# Web
COUNTER,app.requests,web.RequestTracker.count

# Misc
GAUGE,infra.disk,storage.DiskMonitor.record
```

---

### 2. TSV

Tab-separated `col0\tcol1\tcol2`. Column 1 is used for section matching.

```scala
// build.sbt
columnarFmtConfig := ColumnarConfig(
  formatterConfig = ColumnarFormatterConfig.tsv,
  fileGlob        = "**/*.tsv",
  sections        = Seq(
    ColumnarSection("# OS",  primaryPrefixes = Seq("system.")),
    ColumnarSection("# Web", primaryPrefixes = Seq("app.")),
    ColumnarSection("# Misc")
  )
)
```

Input and output follow the same structure as CSV above, with tab as the delimiter.

---

### 3. Pipe-delimited

Input parsed on `|` (spaces around pipes are trimmed); output formatted as `col0 | col1 | col2`.

```scala
// build.sbt
columnarFmtConfig := ColumnarConfig(
  formatterConfig = ColumnarFormatterConfig.pipeDelimited,
  fileGlob        = "**/*.pipe",
  sections        = Seq(
    ColumnarSection("# OS",  primaryPrefixes = Seq("system.")),
    ColumnarSection("# Web", primaryPrefixes = Seq("app.")),
    ColumnarSection("# Misc")
  )
)
```

**Input**:
```
GAUGE|system.cpu|os.CpuMonitor.record
COUNTER|app.requests|web.RequestTracker.count
```

**Output**:
```
# OS
GAUGE | system.cpu | os.CpuMonitor.record

# Web
COUNTER | app.requests | web.RequestTracker.count
```

---

### 4. Play Framework `.routes`

Whitespace-delimited `method  path  controller`. Groups routes by path prefix; falls back to
controller prefix. Sections are output in definition order; empty sections are omitted.

```scala
// build.sbt
columnarFmtConfig := ColumnarConfig(
  formatterConfig = ColumnarFormatterConfig.playRoutes,
  fileGlob        = "conf/app.routes",
  lineLimit       = 120,
  fileHeader      = "# Auto-formatted — do not edit by hand",
  sections        = Seq(
    ColumnarSection("# Infrastructure",
      primaryPrefixes   = Seq("/ping", "/assets"),
      secondaryPrefixes = Seq("controllers.Assets")),
    ColumnarSection("# Public",
      primaryPrefixes   = Seq("/public")),
    ColumnarSection("# Admin",
      primaryPrefixes   = Seq("/admin"),
      lineLimit         = Some(160)),
    ColumnarSection("# Misc")   // catch-all
  )
)
```

**Input** (`conf/app.routes`):
```
GET /ping controllers.HealthController.ping
GET /admin/users controllers.admin.UserController.list
POST /admin/users controllers.admin.UserController.create
GET /public/home controllers.HomeController.index
```

**Output**:
```
# Auto-formatted — do not edit by hand

# Infrastructure
GET  /ping             controllers.HealthController.ping

# Public
GET  /public/home      controllers.HomeController.index

# Admin
GET  /admin/users      controllers.admin.UserController.list
POST /admin/users      controllers.admin.UserController.create
```

---

### 5. Custom format

Supply your own `ColumnarFormatterConfig` for any other fixed-width or delimited format:

```scala
// build.sbt
columnarFmtConfig := ColumnarConfig(
  formatterConfig = ColumnarFormatterConfig(
    parse        = line => {
      val t = line.trim
      if (t.isEmpty || t.startsWith("#")) None
      else {
        val parts = t.split(";", 3).map(_.trim)
        if (parts.length == 3) Some(parts.toIndexedSeq) else None
      }
    },
    primaryCol   = 1,
    secondaryCol = 2,
    dedupeKey    = cols => (cols(0), cols(1)),
    subkeyFn     = cols => cols(2).split("\\.").dropRight(1).mkString("."),
    delimiter    = " ; "
  ),
  fileGlob = "**/*.dat"
)
```

---

## Section options

```scala
ColumnarSection(
  header             = "# My Section",
  primaryPrefixes    = Seq("/api/v1"),         // matched against column primaryCol
  secondaryPrefixes  = Seq("controllers.api"), // fallback match
  lineLimit          = Some(160)               // overrides ColumnarConfig.lineLimit for this section
)
```

Rows are matched using longest-prefix wins. The first section with no prefixes defined
acts as the catch-all for unmatched rows.

---

## License

Apache 2.0 — see [LICENSE](https://www.apache.org/licenses/LICENSE-2.0).