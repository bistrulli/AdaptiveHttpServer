#!/bin/bash 

cd ~/mpclqn/SimpleTask
svn update
mvn clean
mvn package

for i in {0..100..1}
    do
       $JAVA_HOME/bin/java -jar ./target/SimpleTask-0.0.1-SNAPSHOT-jar-with-dependencies.jar 
 done