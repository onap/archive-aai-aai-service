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
# The script to update runner-web.xml to change authentication from basic to 2-way SSL and vice-versa
# 

usage () {
    echo "=========input error==========="
    echo "         Usage: $0 <action>"
    echo "usage examples: $0 disable-basic-auth"
    echo "                $0 enable-basic-auth"
    exit 1
}

prop_file=$PROJECT_HOME/etc/runner-web.xml
prop_name='login-config'

update_prop_disable_basic_auth() {

#	echo "Updating security-constraint and login-config elements in file $prop_file"

	sec_string=`egrep -i "<security-constraint>" $prop_file | grep "<\!--"`
	if [ "$?" -eq "1" ]; then
		sed  -i "s/<security-constraint>/<\!-- <security-constraint>/g" $prop_file
	fi

	config_string=`egrep -i "<\/login-config>" $prop_file | grep "\-->"`
	if [ "$?" -eq "1" ]; then
                sed  -i "s/<\/login-config>/<\/login-config> -->/g" $prop_file
	fi
}

update_prop_enable_basic_auth() {

#	echo "Updating security-constraint and login-config elements in file $prop_file"

	sec_string=`egrep -i "<security-constraint>" $prop_file | grep "<\!--"`
	if [ "$?" -eq "0" ]; then
		sed  -i "s/<\!-- <security-constraint>/<security-constraint>/g" $prop_file
	fi

	config_string=`egrep -i "<\/login-config>" $prop_file | grep "\-->"`
	if [ "$?" -eq "0" ]; then
		sed  -i "s/<\/login-config> -->/<\/login-config>/g" $prop_file
	fi
}

echo `date` "Running $0... Action:$1"
userid=$( id | cut -f2 -d"(" | cut -f1 -d")" )
if [ "${userid}" != "aaiadmin" ]; then
    echo "You must be aaiadmin to run $0. The id used $userid."
    exit 1
fi 

if [ "$#" -ne 1 ]; then
	usage
fi

if [ "$1" = "disable-basic-auth" ]; then
	echo "Updating Authentication Type..."
	update_prop_disable_basic_auth true		
	echo `date` "  Done $0"
    exit 0
fi

if [ "$1" = "enable-basic-auth" ]; then
	echo "Updating Authentication Type..."
	update_prop_enable_basic_auth true		
	echo `date` "  Done $0"
    exit 0
fi
. /etc/profile.d/aai.sh

CLASSPATH=$CLASSPATH:$PROJECT_HOME/extJars/*

for JAR in `ls $PROJECT_HOME/lib/*.jar`
do
     CLASSPATH=$CLASSPATH:$JAR
done

exit 0

