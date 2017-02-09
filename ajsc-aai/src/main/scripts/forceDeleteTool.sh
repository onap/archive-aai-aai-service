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
# Usage:    forceDeleteTool.sh  action  dataString
#
# The script invokes the ForceDelete java class to either collect data about
# nodes that may need to be deleted (when passed the COLLECT_DATA action parameter),
# or to do a delete given a particular vertexId when passed the "DELETE_NODE action parameter.
#
# Ie.   
#     forceDeleteTool COLLECT_DATA "hypervisor-type|esx,operational-state|operationalState"
# or
#     forceDeleteTool DELETE_NODE 98789378
#
#  Note: when collecting data, the dataString looks like: "propertyName1|propertyVal1,propertyName2|propertyVal2" 
#            for however many property names, values you are using to identify this one node.
#  When doing the delete - the dataString has to just be the vertexId of the node you want to delete
#

echo
echo `date` "   Starting $0"

echo " NOTE - if you are deleting data, please run the dataSnapshot.sh script first or "
echo "     at least make a note the details of the node that you are deleting. "

userid=$( id | cut -f2 -d"(" | cut -f1 -d")" )
if [ "${userid}" != "aaiadmin" ]; then
    echo "You must be aaiadmin to run $0. The id used $userid."
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
 org.openecomp.aai.dbgen.ForceDeleteTool $1 "$2"
 
echo `date` "   Done $0"
exit 0
