/jdk-13.0.2/bin/java -Dname=Jorel2 --enable-preview -DdbProfile=$1  -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=1089 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -jar jorel2-0.0.1-SNAPSHOT-jar-with-dependencies.jar