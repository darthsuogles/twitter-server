import sbt._
import Keys._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.site.SphinxSupport.Sphinx
import sbtunidoc.Plugin.unidocSettings
import scoverage.ScoverageSbtPlugin

object TwitterServer extends Build {

  lazy val scalaMajor = "2.10"
  lazy val scalaMinor = "6"

  lazy val scalaVer = s"$scalaMajor.$scalaMinor"

  val branch = Process("git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil).!!.trim
  //val suffix = if (branch == "master") "" else "-SNAPSHOT"
  //val suffix = if (branch == "master") "" else "phi"

  val libVersion = s"1.20.0-$branch"
  //val utilVersion = "6.34.0" + suffix
  //val finagleVersion = "6.35.0" + suffix
  val utilVersion = "6.34.0"
  val finagleVersion = "6.35.0"

  //val jacksonVersion = "2.4.4"
  val jacksonVersion = "2.7.2"
  val jacksonLibs = Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion exclude("com.google.guava", "guava"),
    //"com.google.guava" % "guava" % "16.0.1"
    "com.google.guava" % "guava" % "19.0"
  )

  def util(which: String) = "com.twitter" %% ("util-"+which) % utilVersion
  def finagle(which: String) = "com.twitter" %% ("finagle-"+which) % finagleVersion

  def xmlLib(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 => Seq(
      "org.scala-lang.modules" %% "scala-xml" % "1.0.5"
    )
    case _ => Seq.empty
  }

  val sharedSettings = Seq(
    version := libVersion,
    organization := "com.twitter",
    //scalaVersion := "2.11.7",
    scalaVersion := scalaVer,
    crossScalaVersions := Seq("2.10.6", "2.11.8"),
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test",
      "junit" % "junit" % "4.10" % "test",
      "org.mockito" % "mockito-all" % "1.9.5" % "test"
    ),
    resolvers += "twitter-repo" at "https://maven.twttr.com",

    ScoverageSbtPlugin.ScoverageKeys.coverageHighlighting := (
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 10)) => false
        case _ => true
      }
    ),

    ivyXML :=
      <dependencies>
        <exclude org="com.sun.jmx" module="jmxri" />
        <exclude org="com.sun.jdmk" module="jmxtools" />
        <exclude org="javax.jms" module="jms" />
      </dependencies>,

    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
      "-feature",
      "-Xlint",
      "-encoding", "utf8"
    ),
    javacOptions ++= Seq("-Xlint:unchecked", "-source", "1.7", "-target", "1.7"),
    javacOptions in doc := Seq("-source", "1.7"),

    // This is bad news for things like com.twitter.util.Time
    parallelExecution in Test := false,

    // Sonatype publishing
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishMavenStyle := true,
    autoAPIMappings := true,
    apiURL := Some(url("https://twitter.github.io/twitter-server/docs/")),
    pomExtra :=
      <url>https://github.com/twitter/twitter-server</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:twitter/twitter-server.git</url>
        <connection>scm:git:git@github.com:twitter/twitter-server.git</connection>
      </scm>
      <developers>
        <developer>
          <id>twitter</id>
          <name>Twitter Inc.</name>
          <url>https://www.twitter.com/</url>
        </developer>
      </developers>,
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    }
  )

  lazy val twitterServer = Project(
    id = "twitter-server",
    base = file("."),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings ++
      unidocSettings
  ).settings(
    name := "twitter-server",
    libraryDependencies ++= Seq(
      finagle("core"),
      finagle("http"),
      finagle("zipkin"),
      util("app"),
      util("core"),
      util("events"),
      util("jvm"),
      util("lint"),
      util("logging"),
      util("registry")
    ),
    libraryDependencies ++= jacksonLibs,
    libraryDependencies <++= scalaVersion(xmlLib)
  )

  lazy val twitterServerDoc = Project(
    id = "twitter-server-doc",
    base = file("doc"),
    settings =
      Defaults.coreDefaultSettings ++
      sharedSettings ++
      site.settings ++
      site.sphinxSupport() ++
      Seq(
        scalacOptions in doc <++= version.map(v => Seq("-doc-title", "TwitterServer", "-doc-version", v)),
        includeFilter in Sphinx := ("*.html" | "*.png" | "*.js" | "*.css" | "*.gif" | "*.txt")
      )
    ).configs(DocTest).settings(
      inConfig(DocTest)(Defaults.testSettings): _*
    ).settings(
      unmanagedSourceDirectories in DocTest <+= baseDirectory { _ / "src/sphinx/code" },

      // Make the "test" command run both, test and doctest:test
      test <<= Seq(test in Test, test in DocTest).dependOn
    ).dependsOn(twitterServer)

  /* Test Configuration for running tests on doc sources */
  lazy val DocTest = config("testdoc") extend Test
}
