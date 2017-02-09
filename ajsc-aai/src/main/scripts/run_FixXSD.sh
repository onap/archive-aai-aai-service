#!/bin/ksh

###
# ============LICENSE_START=======================================================
# org.openecomp.aai
# ================================================================================
# Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
# ================================================================================
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#      http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ============LICENSE_END=========================================================
###

#
# The script invokes GetResource java class to get all nodes
#

echo
echo `date` "   Starting $0"

REV=$1
AAIHOME=$2
echo "AAIHOME: $AAIHOME"

if test "$REV" = ""
then
    REV=v6
fi

. /etc/profile.d/aai.sh

if test "$AAIHOME" = ""
then
    AAIHOME=$PROJECT_HOME
fi


for JAR in `ls $AAIHOME/target/*.jar`
do
      CLASSPATH=$CLASSPATH:$JAR
done

for JAR in `ls $PROJECT_HOME/lib/*.jar`
do
     CLASSPATH=$CLASSPATH:$JAR
done

export REV=$REV
echo "FixXSD $REV $AAIHOME"
$JAVA_HOME/bin/java -classpath $CLASSPATH -DAJSC_HOME=$AAIHOME org.openecomp.aai.util.FixXSDNew $REV
ret_code=$?
if [ $ret_code != 0 ]; then
  echo `date` "   Done $0"
  exit $ret_code
fi

echo `date` "   Done $0"
exit 0
