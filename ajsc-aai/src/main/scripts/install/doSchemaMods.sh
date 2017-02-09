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
# this script now requires a release parameter. 
# the tool finds and sorts *.txt files within the 
# bundleconfig/etc/scriptdate/schemamods/$release directory containing
# one or more sets of schemaMod.sh parameters
# this script is run at every installation, logging the schemaMods applied.

# Returns 0 if the specified string contains the specified substring,
# otherwise returns 1.
contains() {
    string="$1"
    substring="$2"
    if test "${string#*$substring}" != "$string"
    then
        return 0    # $substring is in $string
    else
        return 1    # $substring is not in $string
    fi
}

PROGNAME=$(basename $0)
OUTFILE=$PROJECT_HOME/logs/misc/${PROGNAME}.log.$(date +\%Y-\%m-\%d)
#OUTFILE=/c/temp/${PROGNAME}.log.$(date +\%Y-\%m-\%d)

TS=$(date "+%Y-%m-%d %H:%M:%S")

CHECK_USER="aaiadmin"
userid=$( id | cut -f2 -d"(" | cut -f1 -d")" )
if [ "${userid}" != $CHECK_USER ]; then
    echo "You must be  $CHECK_USER to run $0. The id used $userid."
    exit 1
fi 

if [ "$#" -ne 1 ]; then
    echo "Release parameter is required, e.g. 1610, 1702, etc"
    echo "usage: $0 release"
    exit 1
fi

error_exit () {
	echo "${PROGNAME}: failed for ${1:-"Unknown error"} on cmd $2 in $3" 1>&2
	echo "${PROGNAME}: failed for ${1:-"Unknown error"} on cmd $2 in $3" >> $OUTFILE
#	exit ${2:-"1"}
}

rel="/"$1"/"
k=0

ls  $PROJECT_HOME/bundleconfig/etc/scriptdata/schemamods/*/*.txt >/dev/null 2>&1
if [ $? -ne 0 ]
then
echo "No schemaMod to run for release $1";
exit 0;
fi


for filepath in `ls $PROJECT_HOME/bundleconfig/etc/scriptdata/schemamods/*/*.txt|sort -f`
do
contains $filepath $rel
if [ $? -eq 0 ]
then
j=0
while IFS=\n read -r i
do
echo "##### Begin Schema Mods for $i #####" | tee -a $OUTFILE
$PROJECT_HOME/scripts/schemaMod.sh $i >> $OUTFILE 2>&1 || error_exit "$i" $j $filepath 
echo "##### End Schema Mods for $i #####" | tee -a $OUTFILE
j=$(expr "$j" + 1)
k=$(expr "$k" + 1)
done < $filepath

fi
#echo "End schemamod for $filepath" | tee -a $OUTFILE
done
if [ $k -eq 0 ]
then
echo "No schemaMod to run for release $1";
exit 0;
fi

echo "See output and error file: $OUTFILE"

exit 0
