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

import static org.junit.Assert.assertFalse;

import static org.junit.Assert.assertTrue;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;
import org.openecomp.aai.util.DeleteResource;
import org.openecomp.aai.domain.yang.PhysicalLink;



public class DeleteResourceTest {



	/**
	 * Test getInstance.
	 */

	@Test

	public void testGetInstance(){

		Object obj = null;

		try {

			obj = DeleteResource.getInstance(DeleteResource.class);

		} catch (IllegalAccessException | InstantiationException e) {

			e.printStackTrace();

		}

		assertTrue("Didn't get right instance", obj instanceof DeleteResource);

	}

	

	/**
	 * Test GetResourceVersion.
	 */
	@Ignore
	@Test

	public void testGetResourceVersion(){

		String version = "aVersion";

		PhysicalLink plink = new PhysicalLink();

		plink.setResourceVersion(version);

		assertEquals("Versions didn't match", version, DeleteResource.GetResourceVersion(plink));

	}

	

	/**
	 * Test null in GetResourceVersion.
	 */

	@Test

	public void testGetResourceVersion_withNull(){

		PhysicalLink plink = new PhysicalLink();

		assertEquals("Versions didn't match", null, DeleteResource.GetResourceVersion(plink));

	}

	

}

