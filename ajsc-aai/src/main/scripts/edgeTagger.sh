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
# This script is used to synch-up the data in the database with the "edge-tags" definitions found in the  DbEdgeRules.java file.  
# It is not needed during normal operation, but if a new tag (really a property) is defined for an edge or if an
#    existing tag is changed, then this script should be run to migrate existing data so that it matches the new
#    definition.   Note: it is only dealing with the "tags" defined after position 2.  For example, for our existing
#    rules, we have have "isParent" defined in position 2, and then other tags in positions 3, 4 and 5 as 
#    mapped in DbEdgeRules.EdgeInfoMap:
#
#  public static final Map<Integer, String> EdgeInfoMap; 
#    static { 
#        EdgeInfoMap = new HashMap<Integer, String>(); 
#        EdgeInfoMap.put(0, "edgeLabel");
#        EdgeInfoMap.put(1, "direction");
#        EdgeInfoMap.put(2, "isParent" );
#        EdgeInfoMap.put(3, "usesResource" );
#        EdgeInfoMap.put(4, "hasDelTarget" );
#        EdgeInfoMap.put(5, "SVC-INFRA" );
#    }
#  
#   -- The map above is used to interpret the data in the DbEdgeRules.EdgeRules map:
#
#   public static final Multimap<String, String> EdgeRules =
#       new ImmutableSetMultimap.Builder<String, String>() 
#       .putAll("availability-zone|complex","groupsResourcesIn,OUT,false,false,false,reverse")
#       .putAll("availability-zone|service-capability","supportsServiceCapability,OUT,false,false,false,false")
#       .putAll("complex|ctag-pool","hasCtagPool,OUT,true,false,false,false")
#       .putAll("complex|l3-network","usesL3Network,OUT,false,false,false,true")
#       etc...
#
#   -- Valid values for the "tags" can be "true", "false" or "reverse".  Read the T-space
#     write-up for a detailed explanation of this... 
#
#
# To use this script, You can either pass the parameter, "all" to update all the edge rules, or
#   you can pass the KEY to a single edge rule that you would like to update.   NOTE - the 
#   key is that first part of each edge rule that looks like, "nodeTypeA|nodeTypeB".
#
# Ie.   ./edgeTagger.sh "all"
#    or ./edgeTagger.sh "complex|ctag-pool"
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

for JAR in `ls $PROJECT_HOME/lib/*.jar`
do
     CLASSPATH=$CLASSPATH:$JAR
done

$JAVA_HOME/bin/java -classpath $CLASSPATH -Dhttps.protocols=TLSv1.1,TLSv1.2 -DAJSC_HOME=$PROJECT_HOME  -Daai.home=$PROJECT_HOME \
 -Dcom.att.eelf.logging.file=default-logback.xml -Dcom.att.eelf.logging.path="$PROJECT_HOME/bundleconfig/etc/appprops/" \
 org.openecomp.aai.dbgen.UpdateEdgeTags $1
if [ "$?" -ne "0" ]; then
    echo "Problem executing UpdateEdgeTags "
    exit 1
fi

 
echo `date` "   Done $0"
exit 0
