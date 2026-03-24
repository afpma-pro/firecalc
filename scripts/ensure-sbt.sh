#!/bin/bash
# Quick check + setup for sbt in this container
# Safe to run multiple times

export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

if ! command -v sbt &>/dev/null || ! command -v java &>/dev/null; then
    echo "Installing sbt + JDK..."
    apt-get update -qq 2>/dev/null
    apt-get install -y -qq curl gnupg ca-certificates locales openjdk-17-jdk-headless bc 2>/dev/null | tail -2
    sed -i 's/# en_US.UTF-8/en_US.UTF-8/' /etc/locale.gen 2>/dev/null
    locale-gen en_US.UTF-8 2>/dev/null
    mkdir -p /etc/apt/keyrings
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --dearmor -o /etc/apt/keyrings/scalasbt.gpg
    echo "deb [signed-by=/etc/apt/keyrings/scalasbt.gpg] https://repo.scala-sbt.org/scalasbt/debian all main" > /etc/apt/sources.list.d/sbt.list
    echo "deb [signed-by=/etc/apt/keyrings/scalasbt.gpg] https://repo.scala-sbt.org/scalasbt/debian /" > /etc/apt/sources.list.d/sbt_old.list
    apt-get update -qq 2>/dev/null
    apt-get install -y -qq sbt 2>/dev/null | tail -2
fi

echo "sbt=$(which sbt) java=$(java -version 2>&1 | head -1)"
