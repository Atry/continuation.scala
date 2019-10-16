ThisBuild / organization := "com.thoughtworks.continuation"

lazy val continuation = crossProject.crossType(CrossType.Pure)

lazy val continuationJVM = continuation.jvm

lazy val continuationJS = continuation.js

publish / skip := true

parallelExecution in Global := false
