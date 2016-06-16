#!/bin/bash
while read p; do
  #echo $p
  "$JAVA_HOME"/bin/java -jar solverVRP.jar -i $p
 #"$JAVA_HOME"/bin/java -jar solverVRP.jar -i C101.txt

done <files.txt