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

REV=$1
SKIP=$2
LATEST_REV="v8"

export AAIHOME=/media/Users/userid/Documents/MyGit

export script_home=$AAIHOME/src/main/scripts

export YANG_HOME=$AAIHOME/src/main/yang

export REV_HOME=$YANG_HOME/$REV

echo "REV_HOME: " $REV_HOME

cd $REV_HOME

if test "$SKIP" != "POJO"
then
    if [ ! -d "$REV_HOME/xsd" ]
    then
	mkdir xsd
    fi
    
    for i in *.yang 
    do x=`echo $i | cut -d"." -f 1`
	echo $i " " $x; pyang -f xsd $i > xsd/$x.xsd
    done

    echo "Calling $script_home/run_FixXSD.sh $REV $AAIHOME"
#   $script_home/run_FixXSD.sh $REV $AAIHOME

    cd $REV_HOME/xsd
    
    $script_home/make_refs_in_xsd.pl fixedup.xsd > aai${REV}-corrected.xsd
    
    NAMESPACE="http://org.openecomp.aai.inventory/$REV"
    echo "Calling xjc";
    if test "$REV" = "$LATEST_REV"
    then
	xjc -p org.openecomp.aai.domain.yang -b ../bindings.xml aai${REV}-corrected.xsd
	cd org/openecomp/aai/domain/yang
    else 
	xjc -p org.openecomp.aai.domain.yang.${REV} -b ../bindings.xml aai${REV}-corrected.xsd
	cd org/openecomp/aai/domain/yang/${REV}
    fi

    echo "$script_home/fix_yang_classes.pl -n $NAMESPACE $i > $i.new"
    
    for i in *.java
    do $script_home/fix_yang_classes.pl -n $NAMESPACE $i > $i.new
	mv $i $i.bak
	mv $i.new $i
    done

    echo 
    if test "$REV" = "$LATEST_REV"
    then
	rm -f $AAIHOME/src/main/java/org/openecomp/aai/domain/yang/*.java
	cp *.java $AAIHOME/src/main/java/org/openecomp/aai/domain/yang
    else
	rm -f $AAIHOME/src/main/java/org/openecomp/aai/domain/yang/$REV/*.java
	cp *.java $AAIHOME/src/main/java/org/openecomp/aai/domain/yang/$REV
    fi
fi

if test "$SKIP" != "GEN"
then
    $script_home/run_Generator.sh $REV $AAIHOME
fi
