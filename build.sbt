name := "SangriaMongoTest"

version := "1.0"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % "1.1.0",
  "org.sangria-graphql" %% "sangria-spray-json" % "1.0.0",
  "com.typesafe.akka" %% "akka-http" % "10.0.1",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.1",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.1.0",
  "com.github.simplyscala" %% "scalatest-embedmongo" % "0.2.4",

  "org.scalatest" %% "scalatest" % "3.0.1" % Test
)
