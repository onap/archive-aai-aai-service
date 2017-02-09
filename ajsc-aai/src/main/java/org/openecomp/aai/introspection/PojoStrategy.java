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

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.rest.MediaType;
import org.openecomp.aai.annotations.Metadata;

import com.att.aft.dme2.internal.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;

public class PojoStrategy extends Introspector {

	private Object internalObject = null;
	private PojoInjestor injestor = null;
	private Multimap<String, String> keyProps = null;
	private Multimap<String, String> requiredProps = null;
	private Multimap<String, String> altKeyProps = null;
	private Metadata classLevelMetadata = null;
	private Version version;
	private JAXBContext jaxbContext;
	private Marshaller marshaller;
	private Unmarshaller unmarshaller;
	protected PojoStrategy(Object obj, LogLineBuilder llBuilder) {
		super(obj, llBuilder);
		className = PojoStrategy.class.getSimpleName();
		aaiLogger = new AAILogger(PojoStrategy.class.getName());
		this.internalObject = obj;
		injestor = new PojoInjestor();
		classLevelMetadata = obj.getClass().getAnnotation(Metadata.class);

		version = injestor.getVersion(obj.getClass().getName());
		jaxbContext = injestor.getContextForVersion(version);
		super.loader = LoaderFactory.createLoaderForVersion(getModelType(), version, llBuilder);
		try {
			marshaller = jaxbContext.createMarshaller();
			unmarshaller = jaxbContext.createUnmarshaller();
		} catch (JAXBException e) {

		}
		
	}

	private String covertFieldToOutputFormat(String propName) {
		return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, propName);
	}
	
	@Override
	public boolean hasProperty(String name) {
		//TODO 
		return true;
	}
	
	@Override
	/**
	 * Gets the value of the property via reflection
	 */
	public Object get(String name) {
		String getMethodName = "get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, name);
		try {
			return this.internalObject.getClass().getDeclaredMethod(getMethodName).invoke(this.internalObject);			
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			return null;
		}
	}

	@Override
	public void set(String name, Object value) {
		String setMethodName = "set" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, name);
		try {
			this.internalObject.getClass().getDeclaredMethod(setMethodName, value.getClass()).invoke(this.internalObject, value);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			AAIException ex = new AAIException("AAI_4017", e);
			LogLine line = llBuilder.build(className, "set value");
			line.add(name, value.toString());
			aaiLogger.error(ex.getErrorObject(), line, e);
		}
	}

	@Override
	public List<String> getProperties() {
		Field[] fields = this.internalObject.getClass().getDeclaredFields();
		List<String> result = new ArrayList<>();
		
		for (Field field : fields) {
			if (!field.getName().equals("any")) {
				result.add(covertFieldToOutputFormat(field.getName()));
			}
		}
		return result;
	}

	@Override
	public List<String> getRequiredProperties() {
		Field[] fields = this.internalObject.getClass().getDeclaredFields();
		List<String> result = new ArrayList<>();
		
		for (Field field : fields) {
			if (!field.getName().equals("any")) {
				XmlElement annotation = field.getAnnotation(XmlElement.class);
				if (annotation != null) {
					if (annotation.required()) {
						result.add(covertFieldToOutputFormat(field.getName()));
					}
				}
			}
		}
		return result;
	}

	@Override
	public List<String> getKeys() {
		Field[] fields = this.internalObject.getClass().getDeclaredFields();
		List<String> result = new ArrayList<>();
		
		for (Field field : fields) {
			if (!field.getName().equals("any")) {
				Metadata annotation = field.getAnnotation(Metadata.class);
				if (annotation != null) {
					if (annotation.isKey()) {
						result.add(covertFieldToOutputFormat(field.getName()));
					}
				}
			}
		}
		return result;
	}
	
	@Override
	public List<String> getAllKeys() {
		List<String> keys = this.getKeys();
		String altKeys = classLevelMetadata.alternateKeys1();
		if (altKeys != null) {
			String[] altKeysArray = altKeys.split(",");
			for (String altKey : altKeysArray) {
				keys.add(altKey);
			}
		}
		
		return keys;

	}

	public Class<?> getClass(String name) {

		Field field = null;
		try {
			field = this.internalObject.getClass().getDeclaredField(CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, name));
		} catch (NoSuchFieldException | SecurityException e) {
			
			return null;
		}
		
		return field.getType();
	}
	
	public Class<?> getGenericTypeClass(String name) {
		
		try {
			String getMethodName = "get" + CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, name);
			Method method = internalObject.getClass().getDeclaredMethod(getMethodName);
			Type t = method.getGenericReturnType();
			if(t instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType)t;
				return ((Class)pt.getActualTypeArguments()[0]);
			} else {
				return null;
			}
			
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public String getJavaClassName() {
		return internalObject.getClass().getName();
	}
	
	@Override
	public Object getUnderlyingObject() {
		return this.internalObject;
	}
	
	@Override
	public String getName() {
		String className = internalObject.getClass().getSimpleName();
		
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, className);
	}
	
	@Override
	protected String findKey() {
		List<String> keys = null;
		keys = this.getKeys();
		List<String> results = new ArrayList<>();
		for (String key : keys) {
			if (this.getType(key).toLowerCase().contains("long")) {
				key = ((Long)this.getValue(key)).toString();
			} else {
				key = (String)this.getValue(key);
			}
			results.add(key);
		}
		
		return Joiner.on("/").join(results);
	}
	
	@Override
	public String marshal(MarshallerProperties properties) {
		StringWriter result = new StringWriter();
        try {
        	if (properties.getMediaType().equals(MediaType.APPLICATION_JSON_TYPE)) {
				marshaller.setProperty(org.eclipse.persistence.jaxb.MarshallerProperties.MEDIA_TYPE, "application/json");
		        marshaller.setProperty(org.eclipse.persistence.jaxb.MarshallerProperties.JSON_INCLUDE_ROOT, properties.getIncludeRoot());
		        marshaller.setProperty(org.eclipse.persistence.jaxb.MarshallerProperties.JSON_WRAPPER_AS_ARRAY_NAME, properties.getWrapperAsArrayName());
        	}
	        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, properties.getFormatted());
	        marshaller.marshal(this.internalObject, result);
		} catch (JAXBException e) {
			//e.printStackTrace();
		}

        return result.toString();
	}
	
	@Override
	public Object clone() {
		Object result = null;
		 try {
				unmarshaller = jaxbContext.createUnmarshaller();

		        unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");
		        unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);
				unmarshaller.setProperty(UnmarshallerProperties.JSON_WRAPPER_AS_ARRAY_NAME, true);
				
				result = unmarshaller.unmarshal(new StreamSource(new StringReader(this.marshal(true))), this.internalObject.getClass()).getValue();
			 } catch (JAXBException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
			}
		 result = IntrospectorFactory.newInstance(getModelType(), result, llBuilder);
		 return result;
	}
	
	@Override
	public String preProcessKey (String key) {
		String result = "";
		//String trimmedRestURI = restURI.replaceAll("/[\\w\\-]+?/[\\w\\-]+?$", "");
		String[] split = key.split("/");
		int i = 0;
		for (i = split.length-1; i >= 0; i--) {
			
			if (keyProps.containsKey(split[i])) {
				break;
				
			}
			
		}
		result = Joiner.on("/").join(Arrays.copyOfRange(split, 0, i));
		
		return result;
		
	}
	
	@Override
	public ModelType getModelType() {
		return ModelType.POJO;
	}

	@Override
	public String getChildName() {
		String className = internalObject.getClass().getSimpleName();
		String lowerHyphen = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, className);
		
		if (this.isContainer()) {
			lowerHyphen = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,this.getGenericTypeClass(this.getProperties().get(0)).getSimpleName());
		}
		
		return lowerHyphen;
	}

	@Override
	public Map<String, String> getPropertyMetadata(String prop) {
		Field f;
		Map<String, String> result = new HashMap<>();
		try {
			f = internalObject.getClass().getField(prop);
			Metadata m = f.getAnnotation(Metadata.class);
			if (m != null) {
				Field[] fields = m.getClass().getFields();
				String fieldName = "";
				for (Field field : fields) {
					fieldName = field.getName();
					if (fieldName.equals("isAbstract")) {
						fieldName = "abstract";
					} else if (fieldName.equals("extendsFrom")) {
						fieldName = "extends";
					}
					result.put(fieldName, (String)field.get(m));
				}
			}
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
		}
		
		return result;
	}

	@Override
	public String getObjectId() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public String getMetadata(String metadataName) {
		String value = null;
		if ("abstract".equals(metadataName)) {
			metadataName = "isAbstract";
		} else if ("extends".equals(metadataName)) {
			metadataName = "extendsFrom";
		}
		
		try {
			value = (String)this.classLevelMetadata.getClass().getField(metadataName).get(classLevelMetadata);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			//TODO
		}
		
		return value;
	}

	@Override
	public Version getVersion() {
		// TODO Auto-generated method stub
		return null;
	}
}
