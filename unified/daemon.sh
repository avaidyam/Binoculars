#!/bin/bash

# modify this command to select what tool to daemonize.
CMD="java -jar Binoculars.jar"

# start daemon
start() {
    if [ -e "daemon.lock" ]; 
    
    # daemon has already been started!
    then echo "daemon has already been started!";
    else
        nohup $CMD > daemon.log 2>&1 &
        echo $! > daemon.lock
        echo "daemon started"
    fi
}

# stop daemon
stop() {
    if [ -e "daemon.lock" ]; then
        if ps -p `cat daemon.lock` > /dev/null; then
            kill -9 `cat daemon.lock`
            echo "daemon stopped"
        
        # daemon was not running!
        else echo "daemon was not running!"; fi
        rm daemon.lock
    
    # daemon was never started!
    else echo "daemon was never started!"; fi
}

# driver 
case "$1" in
    start) start ;;
    stop) stop ;;
    restart) stop; start ;;
    
    *) # print usage
    echo "usage: ./daemon.sh [start|stop] [command]"
    exit 1
    ;;
esac
