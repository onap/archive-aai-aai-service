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

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.parsers.uri.URIToRelationshipObject;
import org.openecomp.aai.rest.MediaType;
import org.openecomp.aai.workarounds.NamingExceptions;

import com.google.common.base.CaseFormat;

public class MoxyLoader extends Loader {

	private DynamicJAXBContext jaxbContext = null;
	private Unmarshaller unmarshaller = null;
	private final String className = MoxyLoader.class.getSimpleName();
	private AAILogger aaiLogger = new AAILogger(MoxyLoader.class.getName());
	
	/**
	 * Instantiates a new moxy loader.
	 *
	 * @param version the version
	 * @param llBuilder the ll builder
	 */
	protected MoxyLoader(Version version, LogLineBuilder llBuilder) {
		super(version, ModelType.MOXY, llBuilder);
		process(version);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Introspector introspectorFromName(String name) {
		
		Introspector result = null;
		Object temp = this.objectFromName(name);
		if (temp != null) {
			result = IntrospectorFactory.newInstance(ModelType.MOXY, temp, llBuilder);
		}
		return result;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object objectFromName(String name) {
		String upperCamel = "";

		NamingExceptions exceptions = NamingExceptions.getInstance();
		name = exceptions.getObjectName(name);
		//Contains any uppercase, then assume it's upper camel
		if (name.matches(".*[A-Z].*")) {
			upperCamel = name;
		} else {
			upperCamel = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, name);
		}
		
		Object result = null;
		try {
			result = jaxbContext.newDynamicEntity(upperCamel);
		} catch (IllegalArgumentException e) {
			//entity does not exist
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void process(Version version) {
		ModelInjestor injestor = ModelInjestor.getInstance();
		jaxbContext = injestor.getContextForVersion(version);
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Introspector unmarshal(String type, String json, MediaType mediaType) {
		
		DynamicEntity entity = null;
		Introspector result = null;
		Object clazz = this.objectFromName(type);
		 try {
			unmarshaller = jaxbContext.createUnmarshaller();
			if (mediaType.equals(MediaType.APPLICATION_JSON_TYPE)) {
		        unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");
		        unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);
				unmarshaller.setProperty(UnmarshallerProperties.JSON_WRAPPER_AS_ARRAY_NAME, true);
			}
			
			entity = (DynamicEntity)unmarshaller.unmarshal(new StreamSource(new StringReader(json)), clazz.getClass()).getValue();
			result = IntrospectorFactory.newInstance(ModelType.MOXY, entity, llBuilder);
		 } catch (JAXBException e) {
			AAIException ex = new AAIException("AAI_4007", e);
			aaiLogger.error(ex.getErrorObject(), llBuilder.build(className, "could not unmarshal"), e);
		}
		return result;
	}

}
