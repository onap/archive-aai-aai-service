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

import java.util.ArrayList;
import java.util.List;

import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorWalker;
import org.openecomp.aai.logging.LogLineBuilder;

import com.google.common.base.Joiner;


public class InjectResourceVersion extends TestDataGenerator {

	protected List<String> blacklist = new ArrayList<>();
	protected final LogLineBuilder llBuilder = new LogLineBuilder();

	/**
	 * Instantiates a new inject resource version.
	 */
	public InjectResourceVersion() {
		blacklist.add("any");
		blacklist.add("relationship-list");
	}
	
	/**
	 * Adds the resource version deep.
	 *
	 * @param wrappedObj the wrapped obj
	 * @return the introspector
	 */
	public Introspector addResourceVersionDeep(Introspector wrappedObj) {

		Introspector clone = (Introspector)wrappedObj.clone();
		IntrospectorWalker walker = new IntrospectorWalker(this, llBuilder);
		walker.setBlacklist(blacklist);
		walker.walk(clone);
		return clone;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processPrimitive(String propName, Introspector obj) {
		Object result = null;
		if (propName.equals("resource-version")) {
			List<String> keys = obj.getAllKeys();
			List<Object> list = new ArrayList<>();
			for (String key : keys) {
				//Only add the first key for now, there should not be a problem with this method
				//because the it is only being inspected at the object level itself
				list.add(obj.getValue(key));
				break;
			}
			
			result = Joiner.on("/").join(list);
			
		}  else {
			result = obj.getValue(propName);
			
		}
		obj.setValue(propName, result);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processPrimitiveList(String propName, Introspector obj) {

		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processComplexObj(Introspector obj) {

		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void modifyComplexList(List<Object> list, Introspector parent, Introspector child) {

		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean createComplexObjIfNull() {

		return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int createComplexListSize(Introspector parent, Introspector child) {

		return 0;
	}


}
