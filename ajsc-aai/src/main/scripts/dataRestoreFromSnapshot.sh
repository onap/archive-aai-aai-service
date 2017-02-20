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
# This script uses the dataSnapshot and SchemaGenerator (via GenTester) java classes to restore 
# data to a database by doing three things: 
#   1) clear out whatever data and schema are currently in the db 
#   2) rebuild the schema (using the SchemaGenerator)
#   3) reload data from the passed-in datafile (which must found in the dataSnapShots directory and
#      contain an xml view of the db data).
#

echo
echo `date` "   Starting $0"


userid=$( id | cut -f2 -d"(" | cut -f1 -d")" )
if [ "${userid}" != "aaiadmin" ]; then
    echo "You must be aaiadmin to run $0. The id used $userid."
    exit 1
fi 


if [ "$#" -ne 1 ]; then
    echo "Illegal number of parameters"
    echo "usage: $0 previous_snapshot_filename"
    exit 1
fi

. /etc/profile.d/aai.sh

for JAR in `ls $PROJECT_HOME/extJars/*.jar`
do
      CLASSPATH=$CLASSPATH:$JAR
done

for JAR in `ls $PROJECT_HOME/lib/*.jar`
do
     CLASSPATH=$CLASSPATH:$JAR
done

#### Step 1) clear out the database
$JAVA_HOME/bin/java -classpath $CLASSPATH -Dhttps.protocols=TLSv1.1,TLSv1.2 -DAJSC_HOME=$PROJECT_HOME  -Daai.home=$PROJECT_HOME \
 org.openecomp.aai.dbgen.DataSnapshot CLEAR_ENTIRE_DATABASE $1
if [ "$?" -ne "0" ]; then
    echo "Problem clearing out database."
    exit 1
fi
 
#### Step 2) rebuild the db-schema
$JAVA_HOME/bin/java -classpath $CLASSPATH -Dhttps.protocols=TLSv1.1,TLSv1.2 -DAJSC_HOME=$PROJECT_HOME  -Daai.home=$PROJECT_HOME \
 org.openecomp.aai.dbgen.GenTester GEN_DB_WITH_NO_DEFAULT_CR
if [ "$?" -ne "0" ]; then
    echo "Problem rebuilding the schema (SchemaGenerator)."
    exit 1
fi

#### Step 3) reload the data from a snapshot file
$JAVA_HOME/bin/java -classpath $CLASSPATH -Dhttps.protocols=TLSv1.1,TLSv1.2 -DAJSC_HOME=$PROJECT_HOME  -Daai.home=$PROJECT_HOME \
 org.openecomp.aai.dbgen.DataSnapshot RELOAD_DATA $1
if [ "$?" -ne "0" ]; then
    echo "Problem reloading data into the database."
    exit 1
fi
 
 
 
echo `date` "   Done $0"
exit 0
