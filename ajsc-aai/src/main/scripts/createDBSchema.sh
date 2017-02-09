#!/bin/ksh
#
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
#
# The script invokes GenTester java class to create the DB schema
#
# OPTIONS:
#    GEN_DB_WITH_NO_SCHEMA  - use if you want it to create an instance of
#       the graph - but with no schema (this is useful when using the Hbase copyTable to
#       copy our database to different environments).
#       Ie. createDbSchema.sh GEN_DB_WITH_NO_SCHEMA
#
#    CREATE_HBASE_LOG_TBL - use if you want the Hbase logging table
#           to be checked for existence and created if not found.
#    
#    GEN_DB_WITH_NO_DEFAULT_CR - use if you do NOT want the default cloud
#           region generated automatically.  Note - this is moot if you're 
#           using GEN_DB_WITH_NO_SCHEMA
#       
#

echo
echo `date` "   Starting $0"

. /etc/profile.d/aai.sh


for JAR in `ls $PROJECT_HOME/extJars/*.jar`
do
      CLASSPATH=$CLASSPATH:$JAR
done

for JAR in `ls $PROJECT_HOME/lib/*.jar`
do
     CLASSPATH=$CLASSPATH:$JAR
done

$JAVA_HOME/bin/java -classpath $CLASSPATH -Dhttps.protocols=TLSv1.1,TLSv1.2 -DAJSC_HOME=$PROJECT_HOME  -Daai.home=$PROJECT_HOME \
 org.openecomp.aai.dbgen.GenTester $1
 
echo `date` "   Done $0"
exit 0
