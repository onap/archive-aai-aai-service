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

##############################################################################
#
# AAI Startup Script
# ------------------
#
# Changes to the aai application folder
# Adds the jar files into the classpath
# Start the init chef script
# Start the hbase creation of tables script
# Then run the java ajsc aai application via com.att.ajsc.runner.Runner
#
##############################################################################

cd /opt/app/aai;
ARG=$1;

CP=$(find extJars/ lib/ -name "*.jar" | sort | uniq | tr '\n' ':');
COMMONLIBS_PATH=$(find /opt/app/commonLibs -name "*.jar" | tr '\n' ':');

JAVA_OPTS="-XX:MaxPermSize=256m -XX:MaxPermSize=256m -XX:PermSize=32m";
JAVA_OPTS+=" -Dhttps.protocols=TLSv1.1,TLSv1.2";
JAVA_OPTS+=" -DSOACLOUD_SERVICE_VERSION=0.0.1";
JAVA_OPTS+=" -Dlogback.configurationFile=$(pwd)/bundleconfig/etc/logback.xml";
if [ ! -z "${HTTP_PROXY}" ]; then
	IFS=':' read -ra NAMES <<< "$HTTP_PROXY" 
    JAVA_OPTS+=" -Dhttp.proxyHost=${NAMES[0]}:${NAMES[1]}";
    JAVA_OPTS+=" -Dhttp.proxyPort=${NAMES[2]}";
fi
JAVA_OPTS+=" -DAJSC_HOME=$(pwd)";
JAVA_OPTS+=" -DAJSC_CONF_HOME=$(pwd)/bundleconfig/";
JAVA_OPTS+=" -DAJSC_SHARED_CONFIG=$(pwd)/bundleconfig";
JAVA_OPTS+=" -Dplatform=NON-PROD";
JAVA_OPTS+=" -DPid=1306";
JAVA_OPTS+=" -Xmx512m -Xms512m";

JAVA_ARGS="context=/ port=8080 sslport=8443";

JAVA_RUN_FILE="com.att.ajsc.runner.Runner";

if [ ! -z "$ARG" ] && [ "$ARG" = "simple" ]; then
    pkill java;
    java ${JAVA_OPTS} -cp ${CP}${COMMONLIBS_PATH} ${JAVA_RUN_FILE} ${JAVA_ARGS} > /opt/aaihome/aaiadmin/log.out 2>&1 &
else

    PROTOCOL=${PROTOCOL:-https};
    GITLAB_CERTNAME=${GITLAB_CERTNAME};
    GITLAB_USERNAME=${GITLAB_USERNAME};
    GITLAB_PASSWORD=${GITLAB_PASSWORD};

    docker_giturl=${PROTOCOL}://${GITLAB_USERNAME}:${GITLAB_PASSWORD}@${GITLAB_CERTNAME}/${AAI_REPO_PATH};

    cd /var/chef/

    git clone -b ${docker_gitbranch} --single-branch ${docker_giturl}/aai-config.git && \
    git clone -b ${docker_gitbranch} --single-branch ${docker_giturl}/aai-data.git

    /init-chef.sh

    sleep 45;

    cd /opt/app/aai;

    ./bin/createDBSchema.sh;

    java ${JAVA_OPTS} -cp ${CP}${COMMONLIBS_PATH} ${JAVA_RUN_FILE} ${JAVA_ARGS} > /opt/aaihome/aaiadmin/log.out 2>&1 &

    sleep 20;

    . /etc/profile.d/aai.sh

    ./bin/install/updateQueryData.sh

    tail -f /dev/null
fi
