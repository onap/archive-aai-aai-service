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
# The script archives/deletes files that have ".out." in middle of their name (Ie. "dataSnapshot.out.201511102245") that sit 
#      in our log/data directory structure
# 
userid=$( id | cut -f2 -d"(" | cut -f1 -d")" )
if [ "${userid}" != "aaiadmin" ]; then
    echo "You must be aaiadmin to run $0. The id used $userid."
    exit 1
fi 

. /etc/profile.d/aai.sh
PROGNAME=$(basename $0)
LOGDIR=${PROJECT_HOME}/logs
ZIPDIR=ARCHIVE
AGEZIP=30
AGEDELETE=60
ZIPPER=/bin/gzip
DATE=$(date +%Y%m%d)

# Get the program arguments
if [[ $# -lt 3 ]]
then
    echo "${PROGNAME}: Arguments are missing.  Found $#"
    exit 1
fi
LOGDIR="${1}"
AGEZIP=$2
AGEDELETE=$3
if [[ ! -d $LOGDIR ]]
then
    echo "${PROGNAME}: Directory does not exist: ${LOGDIR}"
    exit 1
fi

# Create the archive folder, if it does not exist.
if [[ ! -d ${LOGDIR}/${ZIPDIR} ]]
then
    mkdir ${LOGDIR}/${ZIPDIR}
    if [ $? -ne 0 ]
    then
        echo "${PROGNAME}: Failed to create directory: ${LOGDIR}/${ZIPDIR}"
        exit 1
    fi
    chmod 777 ${LOGDIR}/${ZIPDIR}
fi

# Compress files that are older than AGEZIP days
find -L ${LOGDIR} -name *.gz -prune -o -mtime +${AGEZIP} -type f -name "*.out.*" -exec ${ZIPPER} -f {} \;
find -L ${LOGDIR} -name ${ZIPDIR}  -prune -o -type f -name "*.out.*.gz" -print  | while read i; do dirnm=`dirname ${i}`; drn=`basename ${dirnm}`; mkdir -p ${LOGDIR}/${ZIPDIR}/${drn}; echo ${i}; echo ${LOGDIR}/${ZIPDIR}/${drn}; mv ${i} ${LOGDIR}/${ZIPDIR}/${drn}; done

# Delete archive files that are older than AGEDELETE days
find -L ${LOGDIR}/${ZIPDIR}/* -type f -name "*.gz" -mtime +${AGEDELETE} -exec rm -r {} \;

exit 0
