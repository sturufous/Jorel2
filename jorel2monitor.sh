#!/bin/bash
cd /home/tno/jorel2
if [[ ! $( pgrep -f '/jdk-13.0.2/bin/java -Dname=Jorel2') ]]; then
    /home/tno/jorel2/startupjmx.sh dev
fi
