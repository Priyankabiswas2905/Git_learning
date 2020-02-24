import com.typesafe.sbt.packager.Keys.{bashScriptExtraDefines, batScriptExtraDefines, scriptClasspath}

name := """clowder"""

version := "2.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")

scalaVersion := "2.11.12"

crossScalaVersions := Seq("2.11.12")

libraryDependencies += guice

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.7"

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test

libraryDependencies += "com.typesafe.play" %% "play-mailer" % "2.4.1"

// login
libraryDependencies += "com.unboundid" % "unboundid-ldapsdk" % "4.0.1"

// messagebus
libraryDependencies += "com.rabbitmq" % "amqp-client" % "3.0.0"

// indexing
libraryDependencies += "org.elasticsearch" % "elasticsearch" % "2.3.5" exclude("io.netty", "netty")

// mongo storage
libraryDependencies += "com.novus" %% "salat" % "1.9.9" exclude("org.scala-stm", "scala-stm_2.10.0")

libraryDependencies += "org.mongodb" %% "casbah" % "2.8.2"

// geostreams
libraryDependencies += "org.postgresql" % "postgresql" % "42.1.1"

// Find listing of previewers/stylesheets at runtime
//  servlet is needed here since it is not specified in org.reflections.
//    "javax.servlet" % "servlet-api" % "2.5",
//    "org.reflections" % "reflections" % "0.9.10",
libraryDependencies += "org.reflections" % "reflections" % "0.9.11"

// RDF
libraryDependencies += "org.apache.jena" % "apache-jena-libs" % "3.1.1"

// Used to decode/encode html
libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

// Used for IPP server interaction and tests?
libraryDependencies += "org.apache.httpcomponents" % "httpclient" % "4.2.3"

libraryDependencies += "org.apache.httpcomponents" % "httpcore" % "4.2.3"

libraryDependencies += "org.apache.httpcomponents" % "httpmime" % "4.2.3"

// JSONparser and JSONObject. Used to serialize JSON to XML.
libraryDependencies += "com.googlecode.json-simple" % "json-simple" % "1.1.1"

libraryDependencies += "org.codeartisans" % "org.json" % "20131017"

libraryDependencies += "org.irods.jargon" % "jargon-core" % "3.3.3-beta1"

libraryDependencies += ws

libraryDependencies += "com.typesafe.play" %% "play-iteratees" % "2.6.1"

libraryDependencies += "com.typesafe.play" %% "play-iteratees-reactive-streams" % "2.6.1"


//offline := true
//
//javaOptions in Test += "-Dconfig.file=" + Option(System.getProperty("config.file")).getOrElse("conf/application.conf")
//testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/scalatest-reports")
routesImport += "models._"
routesImport += "util.Binders._"
//routesGenerator := InjectedRoutesGenerator
//TwirlKeys.templateImports += "org.bson.types.ObjectId"
//resolvers += Resolver.url("sbt-plugin-releases", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
//resolvers += Resolver.url("sbt-plugin-snapshots", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns)
//resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/public"
//resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
//resolvers += "Aduna" at "http://maven-us.nuxeo.org/nexus/content/repositories/public/"
////resolvers += "Forth" at "http://139.91.183.63/repository",
//resolvers += "NCSA" at "https://opensource.ncsa.illinois.edu/nexus/content/repositories/thirdparty"
//
//// add custom folder to the classpath, use this to add/modify clowder:
//// custom/public/stylesheets/themes     - for custom themes
//// custom/public/javascripts/previewers - for custom previewers
//// custom/custom.conf                   - to customize application.conf
//scriptClasspath += "../custom"
//
//// same for development mode
//unmanagedClasspath in Runtime += baseDirectory.value / "custom"
//
//// add build number so we can use it in templates
//bashScriptExtraDefines += "addJava \"-Dbuild.version=" + version + "\""
//bashScriptExtraDefines += "addJava \"-Dbuild.bamboo=" + getBambooBuild + "\""
//bashScriptExtraDefines += "addJava \"-Dbuild.branch=" + gitBranchName + "\""
//bashScriptExtraDefines += "addJava \"-Dbuild.gitsha1=" + gitShortHash + "\""
//
//batScriptExtraDefines += "addJava \"-Dbuild.version=" + version + "\""
//batScriptExtraDefines += "addJava \"-Dbuild.bamboo=" + getBambooBuild + "\""
//batScriptExtraDefines += "addJava \"-Dbuild.branch=" + gitBranchName + "\""
//batScriptExtraDefines += "addJava \"-Dbuild.gitsha1=" + gitShortHash + "\""