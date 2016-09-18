#!/bin/bash

SBT_VER=0.13.11
SBT_OPTS="$SBT_OPTS -Dfile.encoding=UTF8"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

set -ex

if [ ! -x ${script_dir}/bin/sbt ]; then (
    cd ${script_dir}
    tarball=sbt-${SBT_VER}.tgz
    [ -f "${tarball}" ] || curl -LO "https://dl.bintray.com/sbt/native-packages/sbt/${SBT_VER}/${tarball}"
    tar -zxvf "${tarball}" --strip-components=1
    [ -x ./bin/sbt ] || chmod +x ./bin/sbt
) fi

test -f ~/.sbtconfig && source $_

java -ea                          \
  ${SBT_OPTS}                     \
  ${JAVA_OPTS}                    \
  -Djava.net.preferIPv4Stack=true \
  -XX:+AggressiveOpts             \
  -XX:+UseParNewGC                \
  -XX:+UseConcMarkSweepGC         \
  -XX:+CMSParallelRemarkEnabled   \
  -XX:+CMSClassUnloadingEnabled   \
  -XX:ReservedCodeCacheSize=128m  \
  -XX:MaxPermSize=1024m           \
  -XX:SurvivorRatio=128           \
  -XX:MaxTenuringThreshold=0      \
  -Xss8M                          \
  -Xms512M                        \
  -Xmx16G                         \
  -server                         \
  -jar "${script_dir}/sbt-launch.jar" "$@"


