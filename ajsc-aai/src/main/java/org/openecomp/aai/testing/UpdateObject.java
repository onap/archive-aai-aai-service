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
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.xml.bind.JAXBException;

import org.json.JSONException;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorWalker;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.serialization.db.EdgeRule;
import org.openecomp.aai.serialization.db.EdgeRules;
import org.openecomp.aai.serialization.db.MultiplicityRule;

public class UpdateObject extends TestDataGenerator {
	
	private PopulateObject pop = new PopulateObject(); 
	
	protected List<String> blacklist = new ArrayList<>();
	protected final LogLineBuilder llBuilder = new LogLineBuilder();
	
	/**
	 * Instantiates a new update object.
	 */
	public UpdateObject() {
		blacklist.add("any");
		blacklist.add("relationship-list");
		blacklist.add("resource-version");
	}
	
	/**
	 * Randomly update field values.
	 *
	 * @param obj the obj
	 * @return the introspector
	 */
	public Introspector randomlyUpdateFieldValues(Introspector obj){
		Introspector clone = null;

		clone = (Introspector)obj.clone();

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
		List<String> keys = obj.getKeys();
		if (!keys.contains(propName)) {
			Random random = new Random();
			int num = random.nextInt(100);
			if (num <= 33) {
				Object val = this.createNewRandomObj(obj.getType(propName));
				obj.setValue(propName, val);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processPrimitiveList(String propName, Introspector obj) {
		Random random = new Random();
		int num = random.nextInt(100);
		if (num <= 33) {
			List<Object> list = (List<Object>) obj.getValue(propName);
			int size = list.size();
			Random rand = new Random();
			int newSize = 0;
			if (size == 0) {
				newSize = rand.nextInt(max);
			} else {
				newSize = rand.nextInt(size);
			}
			list.clear();
			for (int i = 0; i < newSize; i++) {
				list.add(this.createNewRandomObj(obj.getGenericType(propName)));
			}
		}
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
		
		EdgeRule rule;
		int numberOfObjects = max;

		try {
			rule = EdgeRules.getInstance().getEdgeRule(parent.getDbName(), child.getDbName());
			if (rule.getMultiplicityRule().equals(MultiplicityRule.ONE2ONE)) {
				numberOfObjects = 1;
			}
		} catch (AAIException e) {
			System.out.println(e.getErrorObject().getDetails());
		}
		
		int editRemoveOrAdd;
		List<Integer> randNumList = new ArrayList<Integer>();
		Random rand = new Random();
		int numOfItemstoUpdate = 0;
		
		if (numberOfObjects == 1) {
			numOfItemstoUpdate = rand.nextInt(numberOfObjects);
		} else {
			numOfItemstoUpdate = rand.nextInt(numberOfObjects-1)+1;
		}
		for (int i = 0; i < numOfItemstoUpdate; i++){
			randNumList.add(i);
		}
		Collections.shuffle(randNumList);
		for (int i = 0; i < numOfItemstoUpdate; i++) {
			editRemoveOrAdd = rand.nextInt(numberOfObjects);
			int index = randNumList.get(i);
			Introspector newObject = null;
			try {
				newObject = pop.populateRecursively((Introspector)child.clone(), min, max, false);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| InstantiationException | JSONException | IOException | JAXBException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (list.size() < numberOfObjects){
				if (editRemoveOrAdd == 0 && index < list.size()){
					if (newObject != null) {
						list.set(index, newObject.getUnderlyingObject());
					}
				}else if (editRemoveOrAdd == 1 && index < list.size()){
					if (list.size() != 1) {
						list.remove(index);
					}
				}else if (editRemoveOrAdd == 2 && list.size() + 1 <= numberOfObjects){
					if (newObject != null) {
						list.add(index, newObject.getUnderlyingObject());
					}
				}
			}
			
		}
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean createComplexObjIfNull() {
		// TODO Auto-generated method stub
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
