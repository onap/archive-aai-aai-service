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

package org.openecomp.aai.query.builder;

import java.io.UnsupportedEncodingException;
import java.net.URI;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.parsers.query.QueryParser;
import org.openecomp.aai.parsers.query.TraversalStrategy;

/**
 * The Class GremlinTraversal.
 */
public class GremlinTraversal extends GremlinQueryBuilder {

	/**
	 * Instantiates a new gremlin traversal.
	 *
	 * @param loader the loader
	 */
	public GremlinTraversal(Loader loader) {
		super(loader);
		this.factory = new TraversalStrategy(this.loader, this);
	}
	
	/**
	 * Instantiates a new gremlin traversal.
	 *
	 * @param loader the loader
	 * @param start the start
	 */
	public GremlinTraversal(Loader loader, Vertex start) {
		super(loader, start);
		this.factory = new TraversalStrategy(this.loader, this);
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryParser createQueryFromURI(URI uri) throws UnsupportedEncodingException, AAIException {
		return factory.buildURIParser(uri);
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryParser createQueryFromRelationship(Introspector relationship) throws UnsupportedEncodingException, AAIException {
		return factory.buildRelationshipParser(relationship);
	}


	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryParser createQueryFromURI(URI uri, MultivaluedMap<String, String> queryParams)
			throws UnsupportedEncodingException, AAIException {
		return factory.buildURIParser(uri, queryParams);
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder newInstance(Vertex start) {
		return new GremlinTraversal(loader, start);
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public QueryBuilder newInstance() {
		return new GremlinTraversal(loader);
	}
}
