#!/bin/bash

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

#check system vars
: ${AAI_HOME:?"AAI_HOME must be set. This is typically your git root/aai-webapp/all/src"}
: ${SCRIPT_HOME:?"SCRIPT_HOME must be set. This is typically your git root/packages/scripts"}
: ${YANG_OUTPUT:?"YANG_OUTPUT must be set. This should be a temporary directory outside of your git root."}
: ${REV:?"REV must be set. The format is vX where X is the version you'd like to create."}

usage() { 
	echo "-b [use defaults for all prompts] --help [display help]"
}
SKIP=false
while getopts ":b-" opt; do
  case $opt in
    b)
      echo "skipping prompts"
      SKIP=true
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      usage
      exit 1
      ;;
     -)
       case "${OPTARG}" in
       	 help)
	    	usage
	    	exit 1
	    	;;
	     *)
	     	usage
	     	exit 1
	     	;;
	   esac;;
     *)
     	usage
     	exit 1
     	;;
  esac
done


YANGDIR=$AAI_HOME/main/yang/$REV
cd $YANGDIR

if [ ! -d "$YANG_OUTPUT/xsd" ]; then
	mkdir $YANG_OUTPUT/xsd
fi

for i in *.yang ; do x=`echo $i | cut -d"." -f 1`; echo $i " " $x; pyang -f xsd $i > $YANG_OUTPUT/xsd/$x.xsd; done

cd $AAI_HOME/main/java/
javac org/openecomp/aai/util/FixXSDNew.java
java org.openecomp.aai.util.FixXSDNew

rm org/openecomp/aai/util/FixXSDNew.class
cd $YANG_OUTPUT/xsd

$SCRIPT_HOME/make_refs_in_xsd.pl fixedup.xsd > aai$REV-corrected.xsd

echo "compiling class files..."
xjc -p org.openecomp.aai.domain.yang -b $AAI_HOME/main/yang/$REV/bindings.xml aai$REV-corrected.xsd

echo "done!"
cd $YANG_OUTPUT/xsd/org/openecomp/aai/domain/yang/

if [ ! $SKIP = true ]; then
	read -p "Remove relationship classes? (N/y)" choice
else
	choice="N"
fi	  
case "$choice" in 
  y|Y ) echo "removing..."
 	  rm -v Relationship*
 	  ;; 
  * ) echo "skipping removal"
  	  ;;	  	  
esac

echo "fixing yang classes via perl script..."
for i in *.java; do $SCRIPT_HOME/fix_yang_classes.pl -n "http://org.openecomp.aai.inventory/$REV" $i > $i.new; mv $i $i.bak; mv $i.new $i; done

if [ ! $SKIP = true ]; then
	read -p "Copy generated classes into git home? (Y/n)" choice
else
	choice="Y"
fi
case "$choice" in 
  n|N ) echo "classes are at $YANG_OUTPUT";;
  * ) echo "moving files..."
  	  cp -fv *.java $AAI_HOME/main/java/org/openecomp/aai/domain/yang
  	  ;;
esac

echo "generation complete"
exit 1

