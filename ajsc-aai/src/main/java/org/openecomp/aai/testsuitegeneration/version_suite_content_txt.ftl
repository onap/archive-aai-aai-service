<#--
 ============LICENSE_START=======================================================
 org.openecomp.aai
 ================================================================================
 Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 ================================================================================
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 
      http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 ============LICENSE_END=========================================================
-->

!1 Test suite ${version}
!contents -R2 -g -p -f -h!contents -R2 -g -p -f -h
!*> Suite Variables
!define slim.port {${slimPort?c}}
!define defns {http://org.openecomp.aai.inventory/${version}}
!define createStatus {${createStatus}}
!define version {${version}}
!define currentApiVersion {${currentVersion}}
#disable ueb tests
!define uebTest {}
*!
