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

!*> User-defined Variables
!define topFeatureURL_A {!-${topFeatureUrlA}-!}
!define topFeatureURL_B {!-${topFeatureUrlB}-!}
!define featureURL_A {!-${featureUrlA}-!}
!define featureURL_B {!-${featureUrlB}-!}
!define relationshipURLA {!-${relationshipURLA}-!}
!define relationshipURLB {!-${relationshipURLB}-!}
!define body_A {!-${objectBodyA}-!}
!define body_B {!-${objectBodyB}-!}
!define body_RA {!-${objectRelationshipA}-!}
!define body_RB {!-${objectRelationshipB}-!}
!define deletePageA {!include ${deletePageA}}
!define deletePageB {!include ${deletePageB}}
!define expectedResultA {!-${expectedResultA}-!}
!define expectedResultB {!-${expectedResultB}-!}
*!
!include -setup .AAI.Tests.GenericTests.GenericRelationshipTest
