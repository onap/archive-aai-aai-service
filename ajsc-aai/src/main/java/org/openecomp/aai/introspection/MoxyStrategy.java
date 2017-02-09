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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.exceptions.DynamicException;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.eclipse.persistence.mappings.DatabaseMapping;
import org.eclipse.persistence.oxm.XMLField;
import org.eclipse.persistence.oxm.mappings.XMLCompositeCollectionMapping;
import org.eclipse.persistence.oxm.mappings.XMLCompositeDirectCollectionMapping;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.rest.MediaType;
import org.openecomp.aai.workarounds.NamingExceptions;
import org.springframework.web.util.UriUtils;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;

public class MoxyStrategy extends Introspector {
	
	private DynamicEntity internalObject = null;
	private DynamicType internalType = null;
	private DynamicJAXBContext jaxbContext = null;
	private ClassDescriptor cd = null;
	private Marshaller marshaller = null;
	private Unmarshaller unmarshaller = null;
	private Version version = null;
	
	protected MoxyStrategy(Object obj, LogLineBuilder llBuilder) {
		super(obj, llBuilder);
		/* must look up the correct jaxbcontext for this object */
		className = MoxyStrategy.class.getSimpleName();
		aaiLogger = new AAILogger(MoxyStrategy.class.getName());
		internalObject = (DynamicEntity)obj;
		ModelInjestor injestor = ModelInjestor.getInstance();
		version = injestor.getVersionFromClassName(internalObject.getClass().getName());
		jaxbContext = injestor.getContextForVersion(version);
		super.loader = LoaderFactory.createLoaderForVersion(getModelType(), version, llBuilder);
		String simpleName = internalObject.getClass().getName();
		internalType = jaxbContext.getDynamicType(simpleName);
		cd = internalType.getDescriptor();
		try {
			marshaller = jaxbContext.createMarshaller();
			unmarshaller = jaxbContext.createUnmarshaller();
		} catch (JAXBException e) {

		}

	}
	
	@Override
	public boolean hasProperty(String name) {
		String convertedName = convertPropertyName(name);

		return internalType.containsProperty(convertedName);	
	}
	
	@Override
	public Object get(String name) {
		return internalObject.get(name);
	}

	@Override
	public void set(String name, Object obj) throws IllegalArgumentException {
		
		internalObject.set(name, obj);
	}

	@Override
	public List<String> getProperties() {
		List<String> result = new ArrayList<>();
		for (String s : internalType.getPropertiesNames()) {
			result.add(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, s));

		}
		return result;
	}

	@Override
	public List<String> getRequiredProperties() {
		List<String> result = new ArrayList<>();
		for (DatabaseMapping dm : cd.getMappings()) {
			if (dm.getField() instanceof XMLField) { 
				XMLField x = (XMLField)dm.getField();
				if (x != null) { 
					if (x.isRequired()) {
						result.add(this.removeXPathDescriptor(x.getName()));
					}
				}
			}
		}
		return result;
	}

	@Override
	public List<String> getKeys() {
		List<String> result = new ArrayList<>();
		
		for (String name : internalType.getDescriptor().getPrimaryKeyFieldNames()) {
			result.add(this.removeXPathDescriptor(name));
		}
		return result;
	}
	
	@Override
	public Map<String, String> getPropertyMetadata(String prop) {
		String propName = this.convertPropertyName(prop);
		DatabaseMapping mapping = cd.getMappingForAttributeName(propName);
		Map<String, String> result = null;
		if (mapping != null) {
			result = mapping.getProperties();
		}
		
		return result;
	}

	@Override
	public String getJavaClassName() {
		return internalObject.getClass().getName();
	}
	
	

	@Override
	public Class<?> getClass(String name) {
		name = convertPropertyName(name);
		Class<?> resultClass = null;
		try {
			if (internalType.getPropertyType(name) == null) {
				if (cd.getMappingForAttributeName(name) instanceof XMLCompositeDirectCollectionMapping) {
					resultClass = cd.getMappingForAttributeName(name).getContainerPolicy().getContainerClass();
	
				} else if (cd.getMappingForAttributeName(name) instanceof XMLCompositeCollectionMapping) {
					resultClass = cd.getMappingForAttributeName(name).getContainerPolicy().getContainerClass();
				} else {
					ClassDescriptor referenceDiscriptor = cd.getMappingForAttributeName(name).getReferenceDescriptor();
					if (referenceDiscriptor != null) {
						resultClass = referenceDiscriptor.getJavaClass();
					} else {
						resultClass = Object.class;
					}
				}
			} else {
				resultClass = internalType.getPropertyType(name);
			}
		} catch (DynamicException e) {
			//property doesn't exist
		}
		return resultClass;
	}

	@Override
	public Class<?> getGenericTypeClass(String name) {
		name = convertPropertyName(name);
		Class<?> resultClass = null;
		if (internalType.getPropertyType(name) == null) {
			if (cd.getMappingForAttributeName(name) instanceof XMLCompositeDirectCollectionMapping) {
				resultClass = cd.getMappingForAttributeName(name).getFields().get(0).getType();

			} else if (cd.getMappingForAttributeName(name) instanceof XMLCompositeCollectionMapping) {
				resultClass = cd.getMappingForAttributeName(name).getReferenceDescriptor().getJavaClass();
			}
		}
		
		return resultClass;
	}

	@Override
	public Object getUnderlyingObject() {
		return this.internalObject;
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
	public String getName() {
		String className = internalObject.getClass().getSimpleName();
		String lowerHyphen = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, className);
		/*
		if (this.isContainer()) {
			lowerHyphen = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,this.getGenericTypeClass(this.getProperties().get(0)).getSimpleName());
		}*/
		

		return lowerHyphen;
	}
	
	@Override
	public String getObjectId() throws UnsupportedEncodingException {
		String result = "";
		String container = this.getMetadata("container");
		if (this.isContainer()) {
			 result += "/" + this.getName();
		} else {
			
			if (container != null) {
				result += "/" + container;
			}
			result += "/" + this.getDbName() + "/" + this.findKey();
			
		}
		
		return result;
	}
	
	@Override
	protected String findKey() throws UnsupportedEncodingException {
		List<String> keys = null;
		keys = this.getKeys();
		List<String> results = new ArrayList<>();
		for (String key : keys) {
			if (this.getType(key).toLowerCase().contains("long")) {
				key = ((Long)this.getValue(key)).toString();
			} else {
				key = (String)this.getValue(key);
			}
			key = UriUtils.encodePath(key, "UTF-8");

			results.add(key);
		}
		
		return Joiner.on("/").join(results);
	}
	
	@Override
	public String preProcessKey (String key) {
		String result = "";
		//String trimmedRestURI = restURI.replaceAll("/[\\w\\-]+?/[\\w\\-]+?$", "");
		String[] split = key.split("/");
		int i = 0;
		for (i = split.length-1; i >= 0; i--) {
			
			if (jaxbContext.getDynamicType(split[i]) != null) {
				break;
				
			}
			
		}
		result = Joiner.on("/").join(Arrays.copyOfRange(split, 0, i));
		
		return result;
		
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
	public ModelType getModelType() {
		return ModelType.MOXY;
	}
	
	private String removeXPathDescriptor(String name) {
		
		return name.replaceAll("/text\\(\\)", "");
	}

	@Override
	public String getMetadata(String name) {
		String result = "";
		
		result = (String)cd.getProperty(name);
		
		return result;
	}

	@Override
	public Version getVersion() {
		
		return this.version;
	}
}
