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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.xml.bind.JAXBException;

import org.json.JSONException;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorWalker;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.serialization.db.EdgeRule;
import org.openecomp.aai.serialization.db.EdgeRules;
import org.openecomp.aai.serialization.db.MultiplicityRule;
import org.openecomp.aai.testsuitegeneration.AbstractWriter;

public class PopulateObject extends TestDataGenerator {

	protected boolean isMinimumObject = false;
	protected List<String> blacklist = new ArrayList<>();
	protected final LogLineBuilder llBuilder = new LogLineBuilder();

	/**
	 * Instantiates a new populate object.
	 */
	public PopulateObject() {
		blacklist.add("any");
		blacklist.add("relationship-list");
		blacklist.add("resource-version");
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processPrimitive(String propName, Introspector obj) {
		Object val = null;
		String type = obj.getType(propName);
		if (this.isMinimumObject) {
			List<String> required = obj.getRequiredProperties();
			if (!required.contains(propName)) {
				return;
			}
		}
		/* If the key is not a string, don't worry about it being unique */
		if (type.contains("java.lang.String") && isKey(obj, propName)) {
			val = UUID.randomUUID().toString() + AbstractWriter.USERNAME_PLACEHOLDER;
		}else{
			val = createNewRandomObj(type);
			String uniqueProps = obj.getMetadata("uniqueProps");
			if (uniqueProps != null) {
				String[] props = uniqueProps.split(",");
				List<String> list = Arrays.asList(props);
				if (list.contains(propName)) {
					val += AbstractWriter.USERNAME_PLACEHOLDER;
				}
			}
			
		}
		obj.setValue(propName, val);

	}
	
	/**
	 * Populate.
	 *
	 * @param obj the obj
	 * @param isMinimumObject the is minimum object
	 * @return the introspector
	 * @throws JSONException the JSON exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JAXBException the JAXB exception
	 */
	public Introspector populate(Introspector obj, boolean isMinimumObject) throws JSONException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, IOException, JAXBException {
		this.isMinimumObject = isMinimumObject;
		
		IntrospectorWalker walker = new IntrospectorWalker(this, llBuilder);
		walker.setBlacklist(this.blacklist);
		walker.walk(obj);
		return obj;
	}
	
	/**
	 * Populate recursively.
	 *
	 * @param obj the obj
	 * @param min the min
	 * @param max the max
	 * @param isMinimumObject the is minimum object
	 * @return the introspector
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws JSONException the JSON exception
	 * @throws InstantiationException the instantiation exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JAXBException the JAXB exception
	 */
	public Introspector populateRecursively(Introspector obj, int min, int max, boolean isMinimumObject) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, JSONException, InstantiationException, IOException, JAXBException {
		
		IntrospectorWalker walker = new IntrospectorWalker(this, llBuilder);
		this.isMinimumObject = isMinimumObject;
		this.min = min;
		this.max = max;
		walker.setBlacklist(this.blacklist);
		walker.preventCycles(true);
		walker.walk(obj);
		return obj;
		//return populateHelper(o, min, max, true);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processPrimitiveList(String propName, Introspector obj) {
		int numberOfObjects = getRandomInt();

		String propType = "";
		List<Object> list = new ArrayList<>();
		for (int i = 0; i < numberOfObjects; i++) {
			propType = obj.getGenericType(propName);
			Object val = this.createNewRandomObj(propType);
			list.add(val);
		}
		obj.setValue(propName, list);		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processComplexObj(Introspector obj) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void modifyComplexList(List<Object> list, Introspector parent, Introspector child) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean createComplexObjIfNull() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int createComplexListSize(Introspector parent, Introspector child) {
		
		EdgeRule rule;
		int numberOfObjects = getRandomInt();

		try {
			rule = EdgeRules.getInstance().getEdgeRule(parent.getDbName(), child.getDbName());
			if (rule.getMultiplicityRule().equals(MultiplicityRule.ONE2ONE)) {
				numberOfObjects = 1;
			} else if (rule.getMultiplicityRule().equals(MultiplicityRule.ONE2MANY) || rule.getMultiplicityRule().equals(MultiplicityRule.MANY2MANY)) {
				if (numberOfObjects < max) {
					numberOfObjects = max;
				}
			}
		} catch (AAIException e) {
			System.out.println(e.getErrorObject().getDetails());
		}

		
		return numberOfObjects;
	}

	/**
	 * Checks if is key.
	 *
	 * @param obj the obj
	 * @param prop the prop
	 * @return true, if is key
	 */
	private boolean isKey(Introspector obj, String prop) {
		/*String className = trimClassName(obj.getClass().getName());
		Collection<String> keys = keyProps.get(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, className));
		Collection<String> altKeys = altKeysProps.get(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, className));
		String lowerHyphenFieldName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN,f.getName());
		if (keys.contains(lowerHyphenFieldName) || altKeys.contains(lowerHyphenFieldName)){
			return true;
		}
		return false;*/
		
		return obj.getAllKeys().contains(prop);

	}

}
