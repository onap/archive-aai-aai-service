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

package org.openecomp.aai.parsers.query;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.parsers.uri.Parsable;
import org.openecomp.aai.parsers.uri.URIParser;
import org.openecomp.aai.query.builder.QueryBuilder;

/**
 * The Class LegacyQueryParser.
 */
public class LegacyQueryParser extends QueryParser implements Parsable {

	private Introspector previous = null;

	/**
	 * Instantiates a new legacy query parser.
	 *
	 * @param loader the loader
	 * @param queryBuilder the query builder
	 * @param uri the uri
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public LegacyQueryParser(Loader loader, QueryBuilder queryBuilder, URI uri) throws UnsupportedEncodingException, AAIException {
		super(loader, queryBuilder, uri);
		URIParser parser = new URIParser(loader, uri);
		parser.parse(this);
	}
	
	/**
	 * Instantiates a new legacy query parser.
	 *
	 * @param loader the loader
	 * @param queryBuilder the query builder
	 * @param uri the uri
	 * @param queryParams the query params
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public LegacyQueryParser(Loader loader, QueryBuilder queryBuilder, URI uri, MultivaluedMap<String, String> queryParams) throws UnsupportedEncodingException, AAIException {
		super(loader, queryBuilder, uri);
		URIParser parser = new URIParser(loader, uri, queryParams);
		parser.parse(this);
	}

	/**
	 * Instantiates a new legacy query parser.
	 *
	 * @param loader the loader
	 * @param queryBuilder the query builder
	 */
	public LegacyQueryParser(Loader loader, QueryBuilder queryBuilder) {
		super(loader, queryBuilder);
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public void processObject(Introspector obj, Map<String, String> uriKeys) {
		if (previous != null) {
			this.parentResourceType = previous.getDbName();
			queryBuilder.createEdgeTraversal(previous, obj);
		}
		for (String key : uriKeys.keySet()) {
			obj.setValue(key, uriKeys.get(key));
			//TODO probably need to check that these are actually indexed
			queryBuilder.getVerticesByIndexedProperty(key, obj.getValue(key));
		}
		if (previous == null) {
			queryBuilder.createContainerQuery(obj);
		}
		previous = obj;
		this.resultResource = obj.getDbName();
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public void processContainer(Introspector obj, Map<String, String> uriKeys, boolean isFinalContainer) throws AAIException {
		if (isFinalContainer) {
			if (previous != null) {
				this.parentResourceType = previous.getDbName();
				queryBuilder.createEdgeTraversal(previous, obj);
			}
			
			queryBuilder.createContainerQuery(obj);

			if (!uriKeys.isEmpty()) {
				if (previous == null) {
					queryBuilder.formBoundary();
				}
				Introspector child = obj.newIntrospectorInstanceOfNestedProperty(obj.getChildName());
				for (String key : uriKeys.keySet()) {
					try {
						child.setValue(key, uriKeys.get(key));
					} catch (IllegalArgumentException e) {
						throw new AAIException("AAI_3000", e);
					}
					//TODO probably need to check that these are actually indexed
					queryBuilder.getVerticesByIndexedProperty(key, child.getValue(key));
				}
			}
			
			this.resultResource = obj.getChildDBName();
			this.containerResource = obj.getName();
		}
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
	public String getCloudRegionTransform() {
		return "add";
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public boolean useOriginalLoader() {
		return false;
	}
}
