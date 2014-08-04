import AssemblyKeys._

organization := "org.ozb"

name := "ozb-jartools"

version := "0.2"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
	"com.github.scopt" %% "scopt" % "3.1.0" withSources(),
	"org.ozb" %% "ozb-scala-utils" % "0.2" intransitive() withSources()
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

//mainClass in (Compile,run) := Some("org.ozb.utils.jartools.JarFinder")

//mainClass in (Compile,run) := Some("org.ozb.utils.jartools.JarGrep")

assemblySettings

jarName in assembly <<= (name, version) map { (n, v) => n + "-dist-" + v + ".jar" }
