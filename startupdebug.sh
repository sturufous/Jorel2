/jdk-13.0.2/bin/java -Dname=Jorel2 --enable-preview -DdbProfile=$1 -Xdebug -Xrunjdwp:transport=dt_socket,address=*:8998,server=y,suspend=y -jar jorel2-0.0.1-SNAPSHOT-jar-with-dependencies.jar 