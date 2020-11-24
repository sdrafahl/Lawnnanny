val Http4sVersion = "0.21.3"
val CirceVersion = "0.13.0"
val Specs2Version = "4.9.3"
val LogbackVersion = "1.2.3"

resolvers += "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/"
resolvers += Resolver.mavenLocal
resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/"

lazy val apiDependencies = Seq(
  "com.github.jacke" %% "stripe-scala" % "0.5.2",
)

lazy val commonDependencies = Seq(
      "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"      %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "org.specs2"      %% "specs2-core"         % Specs2Version % "test",
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
      "com.typesafe" % "config" % "1.4.0",
      "com.lihaoyi" %% "utest" % "0.7.2" % "test",
      "org.mockito" %% "mockito-scala" % "1.14.2",
      "com.typesafe.slick" %% "slick" % "3.3.2",
      "org.slf4j" % "slf4j-nop" % "1.6.4",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2",
      "org.postgresql" % "postgresql" % "42.2.12",
      "software.amazon.awssdk" % "aws-sdk-java" % "2.13.30",
      "com.github.seratch" %% "awscala-s3" % "0.8.+",
      "jakarta.xml.bind" % "jakarta.xml.bind-api" % "3.0.0-RC3",
      "com.walterjwhite.java.dependencies" % "bouncy-castle" % "0.0.17",
      "org.bouncycastle" % "bcprov-jdk15on" % "1.65.01",
      "com.pauldijou" %% "jwt-circe" % "4.2.0",
      "com.chatwork" %% "scala-jwk" % "1.0.5",
      "org.bitbucket.b_c" % "jose4j" % "0.7.2",
      "co.fs2" %% "fs2-core" % "2.4.0",
      "co.fs2" %% "fs2-io" % "2.4.0",
      "co.fs2" %% "fs2-reactive-streams" % "2.4.0",
      "co.fs2" %% "fs2-experimental" % "2.4.0",
      "com.michaelpollmeier" %% "macros" % "3.4.7.2",
      "org.apache.tinkerpop" % "tinkergraph-gremlin" % "3.3.1",
      "com.tinkerpop.gremlin" % "gremlin" % "2.6.0",
      "org.apache.tinkerpop" % "gremlin-driver" % "3.4.1",
      "com.google.maps" % "google-maps-services" % "0.15.0",
      "org.slf4j" % "slf4j-simple" % "2.0.0-alpha1",
      "io.circe" %% "circe-fs2" % "0.13.0",
      "io.laserdisc" %% "fs2-aws" % "3.0.2",
      "io.laserdisc" %% "fs2-aws-s3" % "3.0.2",
      "eu.timepit" %% "refined" % "0.9.17",
      "com.typesafe.akka" %% "akka-http-core" % "10.2.1",
      "com.typesafe.akka" %% "akka-stream" % "2.6.8"
    )

lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin)
  .settings(
    organization := "com.gardenShare",
    name := "gardenshare",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.1",
    libraryDependencies ++= commonDependencies,
    libraryDependencies ++= apiDependencies,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
  )

lazy val migratorSettings = Seq(
  name := "Migrator",
  libraryDependencies ++= commonDependencies,
  scalaVersion := "2.13.1",
  Compile / run / mainClass := Some("com.gardenShare.gardenshare.migrator.Main")
)

lazy val migrator = (project in file("Migrator"))
  .settings(migratorSettings)
  .dependsOn(root)

dockerfile in docker := {
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre")
    add(artifact, artifactTargetPath)
    expose(8080)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

buildOptions in docker := BuildOptions(cache = false)

assemblyMergeStrategy in assembly := {
 case PathList("META-INF", xs @ _*) => MergeStrategy.discard
 case x => MergeStrategy.first
}

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings",
)

