@echo off
where javac 2> tmp

SET /p javaclocation= < tmp

IF "%javaclocation%"=="INFO: Could not find files for the given pattern(s)." (
  echo Could not find javac--do you need to add C:\Program Files\Java\jdk1.7.0_01\bin or something similar to your PATH variable?
  EXIT /B 1
)



IF "%1"=="" 
(
  
  javac -Xlint:deprecation -Xlint:unchecked -cp .;darwin.jar *.java

) 
ELSE 
(
  
  javac -Xlint:deprecation -Xlint:unchecked -cp .;darwin.jar %1 %2 %3 %4 %5 %6 %7

)



