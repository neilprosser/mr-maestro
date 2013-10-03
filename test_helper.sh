#!/bin/bash

# override properties like:
# export environment_music_scrobbling1_baseurl="http://localhost:${RESTDRIVER_PORT:="8081"}"
TIMEOUT=60

wait_for_port () {
    port=$1
    timeout=$2

    for i in `seq 1 $TIMEOUT`
    do
        if [ `netstat -nl 2>/dev/null | grep -e [\:\.]${SERVICE_PORT:="8080"} | wc -l` -gt 0 ]
        then
            return 0
        fi
        sleep 1
    done

    return 1
}

mongo_pid=
server_pid=
kill_server () {
    if [ $mongo_pid ]; then echo "killing $mongo_pid"; pkill -P $mongo_pid > /dev/null 2>&1; fi
    if [ $server_pid ]; then echo "killing $server_pid"; kill $server_pid; fi
}

handle_force_exit () {
    echo -e "\nHandling interrupt"
    kill_server
    exit 1
}

trap handle_force_exit INT

run_test () {
    type=$1
    timeout=$2
    
    lein embongo& 2>&1
    mongo_pid=$!

    lein trampoline run&
    server_pid=$!
    port=${SERVICE_PORT:="8080"}

    echo "Mongo PID: $mongo_pid"
    echo "PID: $server_pid"
    echo -e "**********\nGiving lein $timeout seconds to build and start the application...\n**********"
    if wait_for_port $port $timeout
    then
        lein midje $1
        at_res=$?

        kill_server
        exit $at_res
    else
        kill_server
        echo "Jetty failed to start, it was not reachable on port $port within $timeout seconds"
        exit 1
    fi
}
