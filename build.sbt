// Name of the project
name := "FS2 + Swing"

// Project version
version := "21.0.0-R32"

// Version of Scala used by the project
scalaVersion := "3.3.1"

lazy val fs2Version = "3.10.2"

libraryDependencies += "co.fs2" %% "fs2-core" % fs2Version
libraryDependencies += "co.fs2" %% "fs2-io" % fs2Version
libraryDependencies += "com.formdev" % "flatlaf" % "3.4.1"
libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "3.0.0"
