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
#
# This script is used to add a target property name with the same value in the database schema 
# to a vertex which has an existing property. The existing property is not removed.
#
# Note also - This script just makes changes to the schema that is currently live.
#    If you were to create a new schema in a brandy-new environment, it would look like
#    whatever the oxm (as of July 2016) told it to look like.   So, part of making a 
#    change to the db schema should Always first be to make the change in the oxm so that
#    future environments will have the change.  This script is just to change existing
#    instances of the schema since schemaGenerator (as of July 2015) does not update things - it 
#    just does the initial creation.
#
# To use this script, you need to pass four parameters:
#      propertyName    -- the name of the property that has the value to be used in the targetProperty
#      targetPropertyName  -- the name of the targetProperty
#      targetNodeType -- NA if all propertyName instances in the DB are impacted, otherwise limit the change to this nodeType
#      skipCommit -- true or false.     For testing, skips the commit when set to true.
#
# Ie.    propertyNameChange service-id persona-model-id service-instance true
#

echo
echo `date` "   Starting $0"


userid=$( id | cut -f2 -d"(" | cut -f1 -d")" )
if [ "${userid}" != "aaiadmin" ]; then
    echo "You must be aaiadmin to run $0. The id used $userid."
    exit 1
fi 


if [ "$#" -ne 4 ]; then
    echo "Illegal number of parameters"
    echo "usage: $0 propertyName targetPropertyName targetNodeType skipCommit"
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

$JAVA_HOME/bin/java -classpath $CLASSPATH -Dhttps.protocols=TLSv1.1,TLSv1.2 -DAJSC_HOME=$PROJECT_HOME  -Daai.home=$PROJECT_HOME \
 org.openecomp.aai.dbgen.PropertyNameChange $1 $2 $3 $4
if [ "$?" -ne "0" ]; then
    echo "Problem executing propertyNameChange "
    exit 1
fi

 
echo `date` "   Done $0"
exit 0
