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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openecomp.aai.logging.LogLineBuilder;

public class IntrospectorWalker {
	
	private Introspector obj;
	private Wanderer w = null;
	private Set<String> blacklist = null;
	private boolean preventCycles = false;
	private final LogLineBuilder llBuilder;
	
	/**
	 * Instantiates a new introspector walker.
	 *
	 * @param w the w
	 * @param llBuilder the ll builder
	 */
	public IntrospectorWalker(Wanderer w, LogLineBuilder llBuilder) {
		this.llBuilder = llBuilder;
		this.w = w;
		this.blacklist = new HashSet<>();
	}
	
	/**
	 * Sets the blacklist.
	 *
	 * @param list the new blacklist
	 */
	public void setBlacklist(List<String> list) {
		blacklist.addAll(list);
	}
	
	/**
	 * Prevent cycles.
	 *
	 * @param prevent the prevent
	 */
	public void preventCycles(boolean prevent) {
		this.preventCycles = prevent;
	}
	
	/**
	 * Walk.
	 *
	 * @param obj the obj
	 */
	public void walk(Introspector obj) {
		walk(obj, null);
	}
	
	/**
	 * Walk.
	 *
	 * @param obj the obj
	 * @param parent the parent
	 */
	private void walk(Introspector obj, Introspector parent) {
		if (preventCycles) {
			blacklist.add(obj.getName()); //so we don't recurse while walking its children
		}
		List<String> props = obj.getProperties();
		w.processComplexObj(obj);
		props.removeAll(blacklist);
		if (!obj.isContainer()) {
			parent = obj;
		}
		for (String prop : props) {
			
			if (obj.isSimpleType(prop)) {
				
				w.processPrimitive(prop, obj);
			} else if (obj.isListType(prop)) {
				
				List list = (List)obj.getValue(prop);
				boolean isComplexType = obj.isComplexGenericType(prop);
				if (isComplexType) {
					Introspector child = obj.newIntrospectorInstanceOfNestedProperty(prop);
					w.modifyComplexList(list, parent, child);
					for (Object item : list) {
							child = IntrospectorFactory.newInstance(obj.getModelType(), item, llBuilder);
							walk(child, parent);
					}
				} else {
					w.processPrimitiveList(prop, obj);
				}
				if (list.size() == 0) {
					if (isComplexType) {
						Introspector child = obj.newIntrospectorInstanceOfNestedProperty(prop);
						int size = w.createComplexListSize(parent, child);
						for (int i = 0; i < size; i++) {
							child = obj.newIntrospectorInstanceOfNestedProperty(prop);
							walk(child, parent);
							list.add(child.getUnderlyingObject());
						}
						
						obj.setValue(prop, list);
					} else if (!isComplexType){
						w.processPrimitiveList(prop, obj);
					}
				}
			
			} else if (obj.isComplexType(prop)) {
				Introspector child = null;
				if (obj.getValue(prop) != null) {
					child = IntrospectorFactory.newInstance(obj.getModelType(), obj.getValue(prop), llBuilder);
				} else {
					if (w.createComplexObjIfNull()) { 
						child = obj.newIntrospectorInstanceOfProperty(prop);
						obj.setValue(prop, child.getUnderlyingObject());
					}
				}
				if (child != null) {
					walk(child, obj);
				}
			}
			
		}
		if (preventCycles) {
			blacklist.remove(obj.getName()); //so we can see it down another path that isn't in danger of recursing over it
		}
	}
}
