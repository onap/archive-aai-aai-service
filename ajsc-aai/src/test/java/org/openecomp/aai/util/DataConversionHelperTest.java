/*-
 * ============LICENSE_START=======================================================
 * org.openecomp.aai
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.openecomp.aai.util;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.openecomp.aai.util.DataConversionHelper;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.rule.PowerMockRule;

public class DataConversionHelperTest {	
	
	@Rule
	public PowerMockRule rule = new PowerMockRule();
	
	static {
	     PowerMockAgent.initializeIfNeeded();
	 }
	
	/**
	 * Test convertIPVersionNumToString with value "4".
	 */
	@Test
	public void testConvertIPVersionNumToString_withNum4(){
		assertEquals(DataConversionHelper.IPVERSION_IPV4, DataConversionHelper.convertIPVersionNumToString("4"));
	}
	
	/**
	 * Test convertIPVersionNumToString with value "6".
	 */
	@Test
	public void testConvertIPVersionNumToString_withNum6(){
		assertEquals(DataConversionHelper.IPVERSION_IPV6, DataConversionHelper.convertIPVersionNumToString("6"));
	}
	
	/**
	 * Test convertIPVersionNumToString with a value other than "4" or "6".
	 */
	@Test
	public void testConvertIPVersionNumToString_withAThirdNumber(){
		assertEquals(DataConversionHelper.IPVERSION_UNKNOWN, DataConversionHelper.convertIPVersionNumToString("-1"));
	}
	
	/**
	 * Test convertIPVersionStringToNum with "v4".
	 */
	@Test
	public void testConvertIPVersionStringToNum_withV4(){
		assertEquals("4", DataConversionHelper.convertIPVersionStringToNum(DataConversionHelper.IPVERSION_IPV4));
	}
	
	/**
	 * Test convertIPVersionStringToNum with "v6".
	 */
	@Test
	public void testConvertIPVersionStringToNum_withV6(){
		assertEquals("6", DataConversionHelper.convertIPVersionStringToNum(DataConversionHelper.IPVERSION_IPV6));
	}
	
	/**
	 * Test convertIPVersionStringToNum with an illegal version.
	 */
	@Test
	public void testConvertIPVersionStringToNum_withRandomString(){
		assertEquals("0", DataConversionHelper.convertIPVersionStringToNum("test string"));
	}
	
	
}
