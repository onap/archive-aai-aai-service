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

package org.openecomp.aai.introspection;

import org.openecomp.aai.logging.LogLineBuilder;

public class IntrospectorFactory {

	/**
	 * New instance.
	 *
	 * @param type the type
	 * @param o the o
	 * @param llBuilder the ll builder
	 * @return the introspector
	 */
	public static Introspector newInstance(ModelType type, Object o, LogLineBuilder llBuilder) {
		
		if (type.equals(ModelType.MOXY)) {
			return new MoxyStrategy(o, llBuilder);
		} else if (type.equals(ModelType.POJO)) {
			return new PojoStrategy(o, llBuilder);
		} else if (type.equals(ModelType.JSON)) {
			return new JSONStrategy(o, llBuilder);
		} else {
			throw new IllegalArgumentException("Unknown class type: " + type); 
		}
		
	}
	
	/**
	 * New instance.
	 *
	 * @param type the type
	 * @param o the o
	 * @param namedType the named type
	 * @param llBuilder the ll builder
	 * @return the introspector
	 */
	public static Introspector newInstance(ModelType type, Object o, String namedType, LogLineBuilder llBuilder) {
		
		if (type.equals(ModelType.JSON)) {
			return new JSONStrategy(o, namedType, llBuilder);
		} else {
			throw new IllegalArgumentException("Unknown class type: " + type); 
		}
		
	}
}
