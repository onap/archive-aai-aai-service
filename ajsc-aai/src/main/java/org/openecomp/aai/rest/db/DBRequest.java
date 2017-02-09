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

package org.openecomp.aai.rest.db;

import java.net.URI;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;

import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.parsers.query.QueryParser;
import org.openecomp.aai.rest.HttpMethod;

/**
 * The Class DBRequest.
 */
public class DBRequest {

	private QueryParser parser = null;
	
	private Introspector introspector = null;
	
	private HttpHeaders headers = null;
	
	private String transactionId = null;
	
	private UriInfo info = null;
	
	private HttpMethod method = null;
	
	private URI uri = null;
	
	private String rawContent = null;
	
	/**
	 * Instantiates a new DB request.
	 *
	 * @param method the method
	 * @param uri the uri
	 * @param parser the parser
	 * @param obj the obj
	 * @param headers the headers
	 * @param info the info
	 * @param transactionId the transaction id
	 */
	public DBRequest(HttpMethod method, URI uri, QueryParser parser, Introspector obj, HttpHeaders headers, UriInfo info, String transactionId) {
		this.method = method;
		this.parser = parser;
		this.introspector = obj;
		this.headers = headers;
		this.transactionId = transactionId;
		this.info = info;
		this.uri = uri;
	}
	
	/**
	 * Gets the headers.
	 *
	 * @return the headers
	 */
	public HttpHeaders getHeaders() {
		return headers;
	}
	
	/**
	 * Sets the headers.
	 *
	 * @param headers the new headers
	 */
	public void setHeaders(HttpHeaders headers) {
		this.headers = headers;
	}
	
	/**
	 * Gets the transaction id.
	 *
	 * @return the transaction id
	 */
	public String getTransactionId() {
		return transactionId;
	}
	
	/**
	 * Sets the transaction id.
	 *
	 * @param transactionId the new transaction id
	 */
	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}
	
	/**
	 * Gets the info.
	 *
	 * @return the info
	 */
	public UriInfo getInfo() {
		return info;
	}
	
	/**
	 * Sets the info.
	 *
	 * @param info the new info
	 */
	public void setInfo(UriInfo info) {
		this.info = info;
	}
	
	/**
	 * Gets the parser.
	 *
	 * @return the parser
	 */
	public QueryParser getParser() {
		return parser;
	}

	/**
	 * Sets the parser.
	 *
	 * @param parser the new parser
	 */
	public void setParser(QueryParser parser) {
		this.parser = parser;
	}

	/**
	 * Gets the introspector.
	 *
	 * @return the introspector
	 */
	public Introspector getIntrospector() {
		return introspector;
	}

	/**
	 * Sets the introspector.
	 *
	 * @param introspector the new introspector
	 */
	public void setIntrospector(Introspector introspector) {
		this.introspector = introspector;
	}

	/**
	 * Gets the method.
	 *
	 * @return the method
	 */
	public HttpMethod getMethod() {
		return method;
	}

	/**
	 * Sets the method.
	 *
	 * @param method the new method
	 */
	public void setMethod(HttpMethod method) {
		this.method = method;
	}

	/**
	 * Gets the uri.
	 *
	 * @return the uri
	 */
	public URI getUri() {
		return uri;
	}

	/**
	 * Sets the uri.
	 *
	 * @param uri the new uri
	 */
	public void setUri(URI uri) {
		this.uri = uri;
	}

	/**
	 * Gets the raw content.
	 *
	 * @return the raw content
	 */
	public String getRawContent() {
		return rawContent;
	}

	/**
	 * Sets the raw content.
	 *
	 * @param rawContent the new raw content
	 */
	public void setRawContent(String rawContent) {
		this.rawContent = rawContent;
	}

}
