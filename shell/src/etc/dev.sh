#!/bin/bash

user=$1
repo="/home/ubuntu/.m2"
base="/home/ubuntu/dev/musshroom"
clp=""
clp="$clp:$base/shell/target/classes"
clp="$clp:$base/common/target/classes"
clp="$clp:$repo/repository/ch.qos.logback/slf4j-api/1.7.5/slf4j-api-1.7.5.jar"
clp="$clp:$repo/repository/org/slf4j/slf4j-api/1.7.5/slf4j-api-1.7.5.jar"
clp="$clp:$repo/repository/ch/qos/logback/logback-core/1.0.13/logback-core-1.0.13.jar"
clp="$clp:$repo/repository/ch/qos/logback/logback-classic/1.0.13/logback-classic-1.0.13.jar"
java -cp "$clp" musshroom.shell.Shell $1

