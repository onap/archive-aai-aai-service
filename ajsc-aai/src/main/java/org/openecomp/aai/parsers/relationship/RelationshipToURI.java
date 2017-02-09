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

package org.openecomp.aai.parsers.relationship;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorFactory;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.serialization.db.EdgeRule;
import org.openecomp.aai.serialization.db.EdgeRules;
import org.openecomp.aai.workarounds.LegacyURLTransformer;

import com.att.aft.dme2.internal.javaxwsrs.core.UriBuilder;

/**
 * The Class RelationshipToURI.
 */
public class RelationshipToURI {
	
	private final String className = RelationshipToURI.class.getSimpleName();
	
	private AAILogger aaiLogger = new AAILogger(RelationshipToURI.class.getName());
	
	private Introspector relationship = null;
	
	private Loader loader = null;
	
	private ModelType modelType = null;
	
	private EdgeRules edgeRules = null;
	
	private URI uri = null;
	
	private LegacyURLTransformer urlTransform = null;
	
	/**
	 * Instantiates a new relationship to URI.
	 *
	 * @param loader the loader
	 * @param relationship the relationship
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public RelationshipToURI(Loader loader, Introspector relationship) throws UnsupportedEncodingException, AAIException {
		this.relationship = relationship;
		this.modelType = relationship.getModelType();
		this.edgeRules = EdgeRules.getInstance();
		this.loader = loader;
		this.urlTransform   = LegacyURLTransformer.getInstance();

		this.parse();
		
	}
	
	/**
	 * Parses the.
	 *
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	protected void parse() throws UnsupportedEncodingException, AAIException {
		String relatedLink = (String)relationship.getValue("related-link");
		StringBuilder uriBuilder = new StringBuilder();
		if (relatedLink != null) {
			try {
				URL url = new URL (relatedLink);
				String path = url.toString();
				uriBuilder.append(url.getPath());
			} catch (MalformedURLException e) {
				AAIException ex = new AAIException("AAI_3009", e);
				aaiLogger.error(ex.getErrorObject(), loader.getLogLineBuilder().build(className, "could not parse url"), e);

			}
		}
		if (uriBuilder.length() == 0) {
			List<Object> data = (List<Object>)relationship.getValue("relationship-data");
			Introspector wrapper = null;
			String key = "";
			String value = "";
			String objectType = "";
			String propertyName = "";
			String[] split = null;
			HashMap<String, Introspector> map = new HashMap<>();
			for (Object datum : data) {
				wrapper = IntrospectorFactory.newInstance(modelType, datum, loader.getLogLineBuilder());
				key = (String)wrapper.getValue("relationship-key");
				value = (String)wrapper.getValue("relationship-value");
				split = key.split("\\.");
				if (split == null || split.length != 2) {
					throw new AAIException("AAI_3000", "incorrect format for key must be of the form {node-type}.{property-name}");
				}
				//check node name ok
				//check prop name ok
				objectType = split[0];
				propertyName = split[1];
				Introspector wrappedObj = loader.introspectorFromName(objectType);
				if (wrappedObj == null) {
					throw new AAIException("AAI_3000", "invalid object name: " + objectType);
				}
				if (!wrappedObj.hasProperty(propertyName)) {
					throw new AAIException("AAI_3000", "invalid property name: " + propertyName);
				}
				if (map.containsKey(objectType)) {
					wrappedObj = map.get(objectType);
				} else {
					map.put(objectType, wrappedObj);
				}
				wrappedObj.setValue(propertyName, value);
				
			}
			String startType = (String)relationship.getValue("related-to");
			List<String> nodeTypes = new ArrayList<>();
			nodeTypes.addAll(map.keySet());
			
			String displacedType = "";
			for (int i = 0; i < nodeTypes.size(); i++) {
				if (nodeTypes.get(i).equals(startType)) {
					displacedType = nodeTypes.set(nodeTypes.size() - 1, startType);
					nodeTypes.set(i, displacedType);
					break;
				}
			}
			sortRelationships(nodeTypes, startType, 1);
			
			for (String type : nodeTypes) {
				uriBuilder.append(map.get(type).getURI());
			}
		}
		
		this.uri = UriBuilder.fromPath(uriBuilder.toString()).build();

	}
	
	/**
	 * Sort relationships.
	 *
	 * @param data the data
	 * @param startType the start type
	 * @param i the i
	 * @return true, if successful
	 */
	private boolean sortRelationships(List<String> data, String startType, int i) {
			
		if (i == data.size()) {
			return true;
		}
		int j = 0;
		String objectType = "";
		String displacedObject = null;
		EdgeRule rule = null;
		String label = "";
		Direction direction = null;
		for (j = (data.size() - i) - 1; j >= 0; j--) {
			objectType = data.get(j);
			try {
				rule = edgeRules.getEdgeRule(objectType, startType);
				label = rule.getLabel();
				direction = rule.getDirection();
			} catch (AAIException e) {
				// TODO Auto-generated catch block
			}
			if (!label.equals("") && (direction != null && direction.equals(Direction.OUT))) {
				displacedObject = data.set((data.size() - i) - 1, data.get(j));
				data.set(j, displacedObject);
				if (sortRelationships(data, objectType, i+1)) {
					return true;
				} else {
					
				}
			}
		}
		

		return false;
	}
	
	/**
	 * Gets the uri.
	 *
	 * @return the uri
	 */
	public URI getUri() {
		return uri;
	}
	
}
