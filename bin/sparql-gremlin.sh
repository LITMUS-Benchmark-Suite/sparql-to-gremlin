#!/bin/bash
#
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

DIR=`dirname $0`

LC=$0
LM=$(find ${DIR}/../ -type f -printf '%T@ %p\n' | grep '\(pom\.xml\|\.java\|src/.*\.sparql\|\.class\)$' | sort -rn | head -n1 | cut -f2 -d ' ')
FN=`basename ${LM}`

if [ "${FN##*.}" != "class" ]; then
  echo -ne "\n\e[2m(re)compiling project ... "
  mvn clean compile > /dev/null 2> /dev/null
  status=$?
  [[ ${status} -eq 0 ]] && echo "OK" || echo -e "FAILED\n"
  echo -ne "\e[0m"
  [[ ${status} -eq 0 ]] || exit ${status}
fi

args=(${@// /\\ })
LAST_COMMAND=${LC} mvn -q exec:java -Dexec.mainClass="com.datastax.sparql.ConsoleCompiler" -Dexec.args="${args[*]}" 2> /dev/null

