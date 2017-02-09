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
# The script invokes the dataGrooming java class to run some tests and generate a report and
#     potentially do some auto-deleteing.
#
# Here are the allowed Parameters.  Note - they are all optional and can be mixed and matched.
#
#  -f oldFileName  (see note below)
#  -autoFix 
#  -sleepMinutes nn
#  -edgesOnly
#  -dontFixOrphans
#  -maxFix
#  -skipHostCheck
#  -singleCommits
#  -dupeCheckOff
#  -dupeFixOn
#  -ghost2CheckOff
#  -ghost2FixOn
#  
# NOTES:
# -f  The name of a previous report can optionally be passed in with the "-f" option. 
#     Just the filename --  ie. "dataGrooming.sh -f dataGrooming.201504272106.out"   
#     The file will be assumed to be in the directory that it was created in.
#     If a filename is passed, then the "deleteCandidate" vertex-id's and bad edges
#     listed inside that report file will be deleted on this run if they are encountered as
#     bad nodes/edges again.
#     
# -autoFix  If you don't use the "-f" option, you could choose to use "-autofix" which will
#           automatically run the script twice: once to look for problems, then after 
#           sleeping for a few minutes, it will re-run with the inital-run's output as
#           an input file.  
#
# -maxFix   When using autoFix, you might want to limit how many 'bad' records get fixed.
#           This is a safeguard against accidently deleting too many records automatically.
#           It has a default value set in AAIConstants:  AAI_GROOMING_DEFAULT_MAX_FIX = 15;
#           If there are more than maxFix candidates found -- then none will be deleted (ie. 
#           someone needs to look into it)
# 
# -sleepMinutes   When using autoFix, this defines how many minutes we sleep before the second run.
#           It has a default value set in AAIConstants:  AAI_GROOMING_DEFAULT_SLEEP_MINUTES = 7;
#           The reason we sleep at all between runs is that our DB is "eventually consistant", so
#           we want to give it time to resolve itself if possible.
#
# -edgesOnly    Can be used any time you want to limit this tool so it only looks at edges.
#           It runs much more quickly when it's just doing edges and sometimes all our
#           problems are with bad edges so it can be nice to focus on edges only sometimes.
#
# -dontFixOrphans   Since there can sometimes be a lot of orphan nodes, and they don't 
#           harm processing as much as phantom-nodes or bad-edges, it is useful to be
#           able to ignore them when fixing things.  
#
# -skipHostCheck    By default, the grooming tool will check to see that it is running
#           on the host that is the first one in the list found in:
#               aaiconfig.properties  aai.primary.filetransfer.serverlist
#           This is so that when run from the cron, it only runs on one machine.
#           This option lets you turn that checking off.
#
# -singleCommits    By default, the grooming tool will do all of its processing and then do
#           a commit of all the changes at once.  This option (maybe could have been named better)
#           is letting the user override the default behavior and do a commit for each
#           individual 'remove" one by one as they are encountered by the grooming logic. 
#           NOTE - this only applies when using either the "-f" or "-autoFix" options since 
#           those are the only two that make changes to the database.
#
# -dupeCheckOff    By default, we will check all of our nodes for duplicates.  This parameter lets
#           us turn this check off if we don't want to do it for some reason.
#
# -dupeFixOn    When we're fixing data, by default we will NOT fix duplicates  This parameter lets us turn 
#           that fixing ON when we are comfortable that it can pick the correct duplicate to preserve. 
#
# -ghost2CheckOff    By default, we will check for the "new" kind of ghost that we saw on
#           Production in early February 2016.  This parameter lets us turn this check off if we 
#           don't want to do it for some reason.
#
# -ghost2FixOn    When we're fixing data, by default we will NOT try to fix the "new" ghost nodes.  
#           This parameter lets us turn that fixing ON if we want to try to fix them. 
#

echo
echo `date` "   Starting $0"


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

for JAR in `ls $PROJECT_HOME/jetty/webapps/*/webapp/WEB-INF/lib/*.jar`
do
	CLASSPATH=$CLASSPATH:$JAR
done

for JAR in `ls $PROJECT_HOME/lib/*.jar`
do
     CLASSPATH=$CLASSPATH:$JAR
done


$JAVA_HOME/bin/java -classpath $CLASSPATH -Dhttps.protocols=TLSv1.1,TLSv1.2 -DAJSC_HOME=$PROJECT_HOME  -Daai.home=$PROJECT_HOME \
 org.openecomp.aai.dbgen.DataGrooming "$@"
 
echo `date` "   Done $0"
exit 0
