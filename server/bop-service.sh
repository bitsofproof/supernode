#!/bin/sh
# Copyright 2013 bits of proof zrt.
# ------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ------------------------------------------------------------------------

#BOP_USER="test"
service=`basename "$0"`
export JVM_FLAGS="-server -Xmx2g"
export BOP_CONTEXTS="leveldb production"

#
# Discover the BOP_BASE from the location of this script.
#
if [ -z "$BOP_BASE" ] ; then

  ## resolve links - $0 may be a link to BOP's home
  PRG="$0"
  saveddir=`pwd`

  # need this for relative symlinks
  dirname_prg=`dirname "$PRG"`
  cd "$dirname_prg"

  while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '.*/.*' > /dev/null; then
      PRG="$link"
    else
      PRG=`dirname "$PRG"`"/$link"
    fi
  done

  BOP_BASE=`dirname "$PRG"`
  cd "$saveddir"

  # make it fully qualified
  BOP_BASE=`cd "$BOP_BASE/.." && pwd`
  export BOP_BASE

fi

PID_FILE="${BOP_BASE}/BOP.pid"

status() {
  if [ -f "${PID_FILE}" ] ; then
    pid=`cat "${PID_FILE}"`
    # check to see if it's gone...
    ps -p ${pid} > /dev/null
    if [ $? -eq 0 ] ; then
      return 1
    else
      rm "${PID_FILE}"
    fi
  fi
  return 0
}

stop() {
  if [ -f "${PID_FILE}" ] ; then
    pid=`cat "${PID_FILE}"`
    kill $@ ${pid} > /dev/null
  fi
  for i in 1 2 3 4 5 ; do
    status
    if [ $? -eq 0 ] ; then
      return 0
    fi
    sleep 1
  done
  echo "Could not stop process ${pid}"
  return 1
}

start() {

  status
  if [ $? -eq 1 ] ; then
    echo "Already running."
    return 1
  fi
 
  if [ -z "$BOP_USER" -o `id -un` = "$BOP_USER" ] ; then
    nohup java ${JVM_FLAGS} -jar ${BOP_BASE}/server/target/bitsofproof-server-1.1.3.jar ${BOP_CONTEXTS} > /dev/null 2> /dev/null &
  else
    sudo -n -u ${BOP_USER} nohup java ${JVM_FLAGS} -jar ${BOP_BASE}/server/target/bitsofproof-server-1.1.3.jar ${BOP_CONTEXTS} > /dev/null 2> /dev/null &
  fi

  echo $! > "${PID_FILE}"

  # check to see if stays up...
  sleep 1
  status
  if [ $? -eq 0 ] ; then
    echo "Could not start ${service}"
    return 1
  fi
  echo "${service} is now running (${pid})"
  return 0
}

case $1 in
  start)
    echo "Starting ${service}"
    start
    exit $?
  ;;

  force-stop)
    echo "Forcibly Stopping ${service}"
    stop -9
    exit $?
  ;;

  stop)
    echo "Gracefully Stopping ${service}"
    stop
    exit $?
  ;;

  restart)
    echo "Restarting ${service}"
    stop
    start
    exit $?
  ;;

  status)
    status
    if [ $? -eq 0 ] ; then
      echo "${service} is stopped"
    else
      echo "${service} is running (${pid})"
    fi
    exit 0
  ;;

  *)
    echo "Usage: $0 {start|stop|restart|force-stop|status}" >&2
    exit 2
  ;;
esac
