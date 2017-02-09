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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openecomp.aai.util.AAIUtils;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.rule.PowerMockRule;

public class AAIUtilsTest {
	
	@Rule
	public PowerMockRule rule = new PowerMockRule();
	
	static {
	     PowerMockAgent.initializeIfNeeded();
	 }
	
	
	AAIUtils testObj;
	
	/**
	 * Initialize.
	 */
	@Before
	public void initialize(){
		testObj = new AAIUtils();
	}
	
	/**
	 * Test nullCheck with null.
	 */
	@Test 
	public void testNullCheck_withNull(){
		List<String> newList = null;
		assertNotNull("method nullCheck should not return null", AAIUtils.nullCheck(newList));
	}
	
	/**
	 * Test nullCheck with a List.
	 */
	@Test
	public void testNullCheck_withList(){
		List<String> newList = new ArrayList<String>();
		newList.add("testString");
		assertNotNull("method nullCheck failed for a List", AAIUtils.nullCheck(newList));
	}
	
	/**
	 * Test genDate using a past and a future date.
	 */
	@Test
	public void testGenDate(){
				
		Date d1 = new Date(0);

		DateFormat formatter = new SimpleDateFormat("YYMMdd-HH:mm:ss:SSS");
		formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		formatter.setLenient(false);

		Date d2 = null;
		
		try {
			d2 = formatter.parse(AAIUtils.genDate());
		} catch (ParseException e) {
			fail("Date parsing exception");
			e.printStackTrace();
		}
		
		try {
			TimeUnit.SECONDS.sleep(1);
		} catch (InterruptedException e1) {}
		
		Date d3 = new Date();

		assertTrue("Generated date is not after a past date", d2.after(d1));
		assertTrue("Generated date is not before a future date", d2.before(d3));
	}

}
