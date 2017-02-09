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
#
# This script is used to change the cardinality of an existing database property.  
# It currently just allows you to change TO a SET cardinality with dataType = String.
# It does not currently let you preserve the data (since we're just doing this for one
#      field which nobody uses yet).    
#
# Note also - This script just makes changes to the schema that is currently live.
#    If you were to create a new schema in a brandy-new environment, it would look like
#    whatever oxm told it to look like.   So, part of making a 
#    change to the db schema should Always first be to make the change in oxm so that
#    future environments will have the change.  This script is just to change existing
#    instances of the schema since schemaGenerator (as of Jan 2016) does not update things - it 
#    just does the initial creation.
#
# Boy, this is getting to be a big comment section...
#
# To use this script, you need to pass four parameters:
#      propertyName    -- the name of the property that you need to change Cardinality on.
#      targetDataType  -- whether it's changing or not, you need to give it:  For now -- we only allow "String"
#      targetCardinality -- For now -- only accepts "SET".   In the future we should support ("SET", "LIST" or "SINGLE")
#      preserveDataFlag -- true or false.     For now -- only supports "false"
#
# Ie.    changePropertyCardinality.sh supplier-release-list String SET false
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
    echo "usage: $0 propertyName targetDataType targetCardinality preserveDataFlag"
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
 org.openecomp.aai.dbgen.ChangePropertyCardinality $1 $2 $3 $4
if [ "$?" -ne "0" ]; then
    echo "Problem executing ChangePropertyCardinality "
    exit 1
fi

 
echo `date` "   Done $0"
exit 0
