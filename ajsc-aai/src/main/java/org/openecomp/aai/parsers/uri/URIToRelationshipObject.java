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

package org.openecomp.aai.parsers.uri;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.rest.LegacyMoxyConsumer;
import org.openecomp.aai.util.AAIApiServerURLBase;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.workarounds.LegacyURLTransformer;
import org.openecomp.aai.workarounds.NamingExceptions;

/**
 * Given a URI a Relationship Object is returned.
 * 
 * The relationship-data objects are created from the keys in the model.
 * The keys are processed in the order they appear in the model.
 
 *
 */
public class URIToRelationshipObject implements Parsable {

	private final String className = URIToRelationshipObject.class.getSimpleName();
	
	private AAILogger aaiLogger = new AAILogger(URIToRelationshipObject.class.getName());

	private Introspector result = null;
	
	private DynamicJAXBContext context = null;
	
	private NamingExceptions exceptions = null;
	
	private LegacyURLTransformer urlTransformer = null;
	
	private Version originalVersion = null;
	
	private Introspector relationship = null;
	
	private Loader loader = null;
	
	/**
	 * Instantiates a new URI to relationship object.
	 *
	 * @param loader the loader
	 * @param uri the uri
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws MalformedURLException the malformed URL exception
	 */
	public URIToRelationshipObject(Loader loader, URI uri) throws IllegalArgumentException, AAIException, UnsupportedEncodingException, MalformedURLException {
		
		this.loader = loader;
		exceptions = NamingExceptions.getInstance();
		urlTransformer = LegacyURLTransformer.getInstance();
		originalVersion = loader.getVersion();
		relationship = loader.introspectorFromName("relationship");
		URIParser parser = new URIParser(loader, uri);
		parser.parse(this);
		URI originalUri = parser.getOriginalURI();
		String baseURL = "";
		try {
			baseURL = AAIApiServerURLBase.get(originalVersion);
		} catch (AAIException e) {
			
			aaiLogger.error(e.getErrorObject(), loader.getLogLineBuilder().build(className, "read property file"), e);
			
		}
		URL relatedLink = new URL(baseURL + originalVersion + "/" + originalUri);
		relationship.setValue("related-link", relatedLink + "/");
		
		
		result = relationship;
	}

	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public String getCloudRegionTransform(){
		return "remove";
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public void processObject(Introspector obj, Map<String, String> uriKeys) {
		

		for (String key : uriKeys.keySet()) {
			
			Introspector data = loader.introspectorFromName("relationship-data");
			data.setValue("relationship-key", obj.getDbName() + "." + key);
			data.setValue("relationship-value", uriKeys.get(key));
			
			((List<Object>)relationship.getValue("relationship-data")).add(data.getUnderlyingObject());
		
		}
		relationship.setValue("related-to", obj.getDbName());
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public void processContainer(Introspector obj, Map<String, String> uriKeys, boolean isFinalContainer) {
		
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public void processNamespace(Introspector obj) {
	
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public boolean useOriginalLoader() {
		return true;
	}
	
	/**
	 * Gets the result.
	 *
	 * @return the result
	 */
	public Introspector getResult() {
		return this.result;
	}
}
