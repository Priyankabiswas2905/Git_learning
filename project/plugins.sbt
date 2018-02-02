// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
//addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.10")
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.11")
//addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.18")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.2.2")

// Create dependency graphs
//addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

// Native packager
//addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.8.0-RC2")

// Show all licenses
//addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.0.0")

