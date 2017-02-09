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

package org.openecomp.aai.testing;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang.RandomStringUtils;
import org.openecomp.aai.introspection.Wanderer;

public abstract class TestDataGenerator implements Wanderer {

	protected int minStringLength = 3;
	protected int maxStringLength = 13;
	protected int min = 1;
	protected int max = 2;
	
	/**
	 * Creates the new random obj.
	 *
	 * @param type the type
	 * @return the object
	 */
	protected Object createNewRandomObj (String type) {
		Object newObj = null;
		if ( type.contains("java.lang.String")) {
				newObj = RandomStringUtils.randomAlphanumeric(ThreadLocalRandom.current().nextInt(minStringLength, maxStringLength + 1));
		} else if ( type.toLowerCase().equals("long") ||type.contains("java.lang.Long")) {
			newObj = new Long(RandomStringUtils.randomNumeric(ThreadLocalRandom.current().nextInt(minStringLength, minStringLength + 1)));
		} else if(type.toLowerCase().equals("boolean") || type.contains("java.lang.Boolean")){
			Random rand = new Random();
			newObj = rand.nextBoolean();
		}else if ( type.toLowerCase().equals("int") || type.contains("java.lang.Integer")){
			newObj = new Integer(RandomStringUtils.randomNumeric(ThreadLocalRandom.current().nextInt(minStringLength, minStringLength + 1)));
		} 
		
		return newObj;
	}
	
	/**
	 * Gets the random int.
	 *
	 * @return the random int
	 */
	protected int getRandomInt() {
		return ThreadLocalRandom.current().nextInt(this.min, this.max + 1);
	}
}
