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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import org.eclipse.persistence.exceptions.DynamicException;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.rest.MediaType;
import org.openecomp.aai.workarounds.NamingExceptions;

import com.google.common.base.CaseFormat;

public abstract class Introspector implements Cloneable {

	protected String className;
	protected AAILogger aaiLogger;
	protected String uriChain = "";
	protected final LogLineBuilder llBuilder;
	protected Loader loader;
	protected final NamingExceptions namingException = NamingExceptions.getInstance();

	protected Introspector(Object obj, LogLineBuilder llBuilder) {
		this.llBuilder = llBuilder;
	}
	public abstract boolean hasProperty(String name);

	protected String convertPropertyName (String name) {
		return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, name);
	}
	
	protected abstract Object get(String name);
	protected abstract void set(String name, Object value);
	/**
	 * 
	 * @param name the property name you'd like to retrieve the value for
	 * @return the value of the property
	 */
	public Object getValue(String name) {
		String convertedName = convertPropertyName(name);
		Object result = null;
		
		if (this.hasProperty(name)) {
			result = this.get(convertedName);
		} else {
			/* property not found - slightly ambiguous */
			return null;
		}
		
		Class<?> clazz = this.getClass(name);
		if (this.isListType(name) && result == null) {
			try {
				this.set(convertedName, clazz.newInstance());
				result = this.get(convertedName);
			} catch (DynamicException | InstantiationException | IllegalAccessException e) {
				
			}
		}

		return result;
	}
	/**
	 * 
	 * @param name the property name you'd like to set the value of
	 * @param obj the value to be set
	 * @return
	 */
	public void setValue(String name, Object obj) throws IllegalArgumentException {
		Object box = obj;
		Class<?> nameClass = this.getClass(name);
		if (nameClass == null) {
			throw new IllegalArgumentException("property: " + name + " does not exist on " + this.getDbName());
		}
		if (obj != null) {
	
			try {
				if (!obj.getClass().getName().equals(nameClass.getName())) {
					if (obj.getClass().getName().equals("java.lang.String")) {
						box = nameClass.getConstructor(String.class).newInstance(obj);
					} else if (!this.isListType(name)){
						box = obj.toString();
					}
				}
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				AAIException ex = new AAIException("AAI_4017", e);
				LogLine line = llBuilder.build(className, "set value");
				line.add(name, obj.toString());
				aaiLogger.error(ex.getErrorObject(), line, e);
			
			}
		}
		
		
		name = convertPropertyName(name);
		this.set(name, box);
	}
	/**
	 * 
	 * @return a list of all the properties available on the object
	 */
	public abstract List<String> getProperties();
	/**
	 * 
	 * @return a list of the required properties on the object
	 */
	public abstract List<String> getRequiredProperties();
	/**
	 * 
	 * @return a list of the properties that can be used to query the object in the db
	 */
	public abstract List<String> getKeys();
	/**
	 * 
	 * @return a list of the all key properties for this object
	 */
	public List<String> getAllKeys() {
		List<String> keys = this.getKeys();
		String altKeys = this.getMetadata("alternateKeys1");
		if (altKeys != null) {
			String[] altKeysArray = altKeys.split(",");
			for (String altKey : altKeysArray) {
				keys.add(altKey);
			}
		}
		
		return keys;
	}

	public List<String> getIndexedProperties() {
		List<String> keys = this.getKeys();
		String altKeys = this.getMetadata("indexedProps");
		if (altKeys != null) {
			String[] altKeysArray = altKeys.split(",");
			for (String altKey : altKeysArray) {
				keys.add(altKey);
			}
		}
		
		return keys;
	}
	/**
	 * 
	 * @param name
	 * @return the string name of the java class of the named property
	 */
	public String getType(String name) {
		Class<?> resultClass = this.getClass(name);
		String result = "";
		
		if (resultClass != null) {
			result = resultClass.getName();
			if (result.equals("java.util.ArrayList")) {
				result = "java.util.List";
			}
		}
		
		return result;
	}
	/**
	 * This will returned the generic parameterized type of the underlying
	 * object if it exists
	 * @param name
	 * @return the generic type of the java class of the underlying object
	 */
	public String getGenericType(String name) {
		Class<?> resultClass = this.getGenericTypeClass(name);
		String result = "";
		
		if (resultClass != null) {
			result = resultClass.getName();
		}
		
		return result;
	}
	/**
	 * 
	 * @return the string name of the java class of the underlying object
	 */
	public abstract String getJavaClassName();
	
	/**
	 * 
	 * @param name the property name
	 * @return the Class object
	 */
	public abstract Class<?> getClass(String name);
	
	public abstract Class<?> getGenericTypeClass(String name);

	/**
	 * 
	 * @param name the property name
	 * @return a new instance of the underlying type of this property
	 */
	public Object newInstanceOfProperty(String name) {
		String type = this.getType(name);
		return loader.objectFromName(type);
	}

	public Object newInstanceOfNestedProperty(String name) {
		String type = this.getGenericType(name);
		return loader.objectFromName(type);
	}
	
	
	public Introspector newIntrospectorInstanceOfProperty(String name) {
	
		Introspector result = IntrospectorFactory.newInstance(this.getModelType(), this.newInstanceOfProperty(name), llBuilder);
		
		return result;
		
	}
	
	public Introspector newIntrospectorInstanceOfNestedProperty(String name) {
	
		Introspector result = IntrospectorFactory.newInstance(this.getModelType(), this.newInstanceOfNestedProperty(name), llBuilder);
		
		return result;
		
	}
	/**
	 * Is this type not a Java String or primitive
	 * @param name
	 * @return
	 */
	public boolean isComplexType(String name) {
		String result = this.getType(name);
		
		if (result.contains("aai")) {
			return true;
		} else {
			return false;
		}
	}
	
	public boolean isComplexGenericType(String name) {
		String result = this.getGenericType(name);
		
		if (result.contains("aai")) {
			return true;
		} else {
			return false;
		}
	}
	
	public boolean isSimpleType(String name) {
		return !(this.isComplexType(name) || this.isListType(name));
	}
	
	public boolean isSimpleGenericType(String name) {
		return !this.isComplexGenericType(name);
	}

	public boolean isListType(String name) {
		String result = this.getType(name);
		
		if (result.contains("java.util.List")) {
			return true;
		} else {
			return false;
		}
	}
	
	public boolean isContainer() {
		List<String> props = this.getProperties();
		boolean result = false;
		if (props.size() == 1 && this.isListType(props.get(0))) {
			result = true;
		}
		
		return result;
	}
	
	public abstract String getChildName();
	public String getChildDBName() {
		String result = this.getChildName();
		
		result = namingException.getDBName(result);
		return result;
	}
	public abstract String getName();

	public String getDbName() {
		String lowerHyphen = this.getName();

		lowerHyphen = namingException.getDBName(lowerHyphen);
		
		return lowerHyphen;
	}
		
	public abstract ModelType getModelType();

	public boolean hasChild(Introspector child) {
		boolean result = false;
		//check all inheriting types for this child
		if ("true".equals(this.getMetadata("abstract"))) {
			String[] inheritors = this.getMetadata("inheritors").split(",");
			for (String inheritor : inheritors) {
				Introspector temp = this.loader.introspectorFromName(inheritor);
				result = temp.hasProperty(child.getName());
				if (result) {
					break;
				}
			}
		} else {
			result = this.hasProperty(child.getName());
		}
		return result;
	}
	
	public void setURIChain(String uri) {
		this.uriChain = uri;
	}
	public abstract String getObjectId() throws UnsupportedEncodingException;

	public String getURI() throws UnsupportedEncodingException {
		//String result = this.uriChain;
		String result = "";
		String namespace = this.getMetadata("namespace");
		String container = this.getMetadata("container");
		if (this.isContainer()) {
			 result += "/" + this.getName();
		} else {
			
			if (container != null) {
				result += "/" + container;
			}
			result += "/" + this.getDbName() + "/" + this.findKey();
			
			if (namespace != null && !namespace.equals("")) {
				result = "/" + namespace + result;
			}
		}
		

		return result;
	}
	
	public String getGenericURI() {
		String result = "";
		if (this.isContainer()) {
			 result += "/" + this.getName();
		} else {
			result += "/" + this.getDbName();
			for (String key : this.getKeys()) {
				result += "/{" + this.getDbName() + "-" + key + "}";
			}
		}
		
		return result;
	}
	
	public String getFullGenericURI() {
		String result = "";
		String namespace = this.getMetadata("namespace");
		String container = this.getMetadata("container");
		if (this.isContainer()) {
			 result += "/" + this.getName();
		} else {


			if (container != null) {
				result += "/" + container;
			}
			result += "/" + this.getDbName();

			for (String key : this.getKeys()) {
				result += "/{" + this.getDbName() + "-" + key + "}";
			}
			if (namespace != null && !namespace.equals("")) {
				result = "/" + namespace + result;
			}
			
		}
		
		return result;
	}

	public abstract String preProcessKey(String key);
	
	protected abstract String findKey() throws UnsupportedEncodingException;
	
	public abstract String marshal(MarshallerProperties properties);
	
	public abstract Object clone();

	public abstract Object getUnderlyingObject();
	
	public String marshal(boolean formatted) {
		MarshallerProperties properties =
				new MarshallerProperties.Builder(MediaType.APPLICATION_JSON_TYPE).formatted(formatted).build();
		
		return marshal(properties);
	}
	public String makeSingular(String word) {
		
		String result = word;
		result = result.replaceAll("(?:([ho])es|s)$", "");
		
		if (result.equals("ClassesOfService")) {
			result = "ClassOfService";
		} else if (result.equals("CvlanTag")) {
			result = "CvlanTagEntry";
		} else if (result.equals("Metadata")) {
			result = "Metadatum";
		}
		return result;
	}
	
	protected String makePlural(String word) {
		String result = word;
		
		if (result.equals("cvlan-tag-entry")) {
			return "cvlan-tags";
		} else if (result.equals("class-of-service")) {
			return "classes-of-service";
		} else if (result.equals("metadatum")) {
			return "metadata";
		}
		result = result.replaceAll("([a-z])$", "$1s");
		result = result.replaceAll("([hox])s$", "$1es");
		/*
		if (result.equals("classes-of-services")) {
			result = "classes-of-service";
		}*/
		
		return result;
	}

	public abstract String getMetadata(String metadataName);
	public abstract Map<String, String> getPropertyMetadata(String prop);
	
	public abstract Version getVersion();
}
