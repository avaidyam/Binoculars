#!/bin/bash

# modify this command to select what tool to daemonize.
CMD="/usr/lib/jvm/java-8-oracle/bin/java -jar Binoculars.jar"

# daemonizer: 
case "$1" in
    start) # start daemon
    if [ -e "daemon.lock" ]; 
    
    # daemon has already been started!
    then echo "daemon has already been started!";
    else
        nohup $CMD >> daemon.log 2>&1 &
        echo $! > daemon.lock
        echo "daemon started"
    fi
    ;;

    stop) # stop daemon
    if [ -e "daemon.lock" ]; then
        if ps -p `cat daemon.lock` > /dev/null; then
            kill -9 `cat daemon.lock`
            echo "daemon stopped"
        
        # daemon was not running!
        else echo "daemon was not running!"; fi
        rm daemon.lock
    
    # daemon was never started!
    else echo "daemon was never started!"; fi
    ;;
    
    *) # print usage
    echo "usage: ./daemon.sh [start|stop] [command]"
    exit 1
    ;;
esac
