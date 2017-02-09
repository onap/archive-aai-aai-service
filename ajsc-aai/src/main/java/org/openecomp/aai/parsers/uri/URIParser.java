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
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.MultivaluedMap;

import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.util.AAIConfig;
import org.springframework.web.util.UriUtils;

import com.att.aft.dme2.internal.javaxwsrs.core.UriBuilder;

/**
 * The Class URIParser.
 */
public class URIParser {

	private final String className = URIParser.class.getSimpleName();
	
	private AAILogger aaiLogger = new AAILogger(URIParser.class.getName());

	protected URI uri = null;
	
	protected Loader loader = null;
	
	protected Loader originalLoader = null;
	
	private URI originalURI = null;
	
	private MultivaluedMap<String, String> queryParams = null;
	
	private final LogLineBuilder llBuilder;
	
	/**
	 * Instantiates a new URI parser.
	 *
	 * @param loader the loader
	 * @param uri the uri
	 */
	public URIParser(Loader loader, URI uri) {
		this.llBuilder = loader.getLogLineBuilder();
		this.uri = uri;
		/*Pattern p = Pattern.compile("(v\\d+)\\/");
		Matcher m = p.matcher(uri);
		Version version = null;
		if (m.find()) {
			version = Version.valueOf(m.group(1));
		}
		this.version = version;*/
		String currentVersion = "v7";
		this.originalLoader = loader;
		try {
			currentVersion = AAIConfig.get("aai.default.api.version");
		} catch (AAIException e) {
			aaiLogger.error(e.getErrorObject(), llBuilder.build(className, "read property file"), e);

		}
		this.loader = loader;
	}
	
	/**
	 * Instantiates a new URI parser.
	 *
	 * @param loader the loader
	 * @param uri the uri
	 * @param queryParams the query params
	 */
	public URIParser(Loader loader, URI uri, MultivaluedMap<String, String> queryParams) {
		this(loader, uri);
		this.queryParams = queryParams;
	}

	public Loader getLoader() {
		
		return this.loader;
		
	}
	
	/**
	 * Gets the original URI.
	 *
	 * @return the original URI
	 */
	public URI getOriginalURI() {
		return this.originalURI;
	}
	
	/**
	 * Parses the.
	 *
	 * @param p the p
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	public void parse(Parsable p) throws UnsupportedEncodingException, AAIException {
		try {
			uri = this.trimURI(uri);
//			uri = handleCloudRegion(p.getCloudRegionTransform(), uri);
			if (p.useOriginalLoader()) {
				this.loader = this.originalLoader;
			}
			this.originalURI  = UriBuilder.fromPath(uri.getRawPath()).build();
			String[] parts = uri.getRawPath().split("/");
			Introspector validNamespaces = loader.introspectorFromName("inventory");
			List<String> keys = null;
			String part = "";
			Introspector previousObj = null;

			for (int i = 0; i < parts.length;) {
				part = parts[i];
				Introspector introspector = null;
				introspector = loader.introspectorFromName(part);
				if (introspector != null) {
					
					//previous has current as property
					if (previousObj != null && !previousObj.hasChild(introspector)) {
						throw new AAIException("AAI_3001", uri + " not a valid path. " + part + " not valid");
					} else if (previousObj == null) {
						String abstractType = introspector.getMetadata("abstract");
						if (abstractType == null) {
							abstractType = "";
						}
						//first time through, make sure it starts from a namespace
						//ignore abstract types
						if (!abstractType.equals("true") && !validNamespaces.hasChild(introspector)) {
							throw new AAIException("AAI_3000", uri + " not a valid path. It does not start from a valid namespace");
						}
					}
					
					keys = introspector.getKeys();
					if (keys.size() > 0) {
						Map<String, String> uriKeys = new LinkedHashMap<>();
						i++;
						if (i == parts.length && queryParams != null) {
							Set<String> queryKeys = queryParams.keySet();
							for (String key : queryKeys) {
								queryParams.get(key);
								for (String value : queryParams.get(key)) {
									value =  UriUtils.decode(value, "UTF-8");
									
									uriKeys.put(key, value);
									
								}
							}
						} else {
							for (String key : keys) {
								part =  UriUtils.decode(parts[i], "UTF-8");
								
								uriKeys.put(key, part);
								
								//skip this for further processing
								i++;
							}
						}
						
						p.processObject(introspector, uriKeys);
	
					} else if (introspector.isContainer()) {
						boolean isFinalContainer = i == parts.length-1;
						Map<String, String> uriKeys = new LinkedHashMap<>();
						
						if (isFinalContainer && queryParams != null) {
							Set<String> queryKeys = queryParams.keySet();
							for (String key : queryKeys) {
								queryParams.get(key);
								for (String value : queryParams.get(key)) {
									value =  UriUtils.decode(value, "UTF-8");
									
									uriKeys.put(key, value);
									
								}
							}
						}
						p.processContainer(introspector, uriKeys, isFinalContainer);
						
						i++; 
					} else {
						p.processNamespace(introspector);
						//namespace case
						i++;
					}
					previousObj = introspector;
				} else {
					//invalid item found should log
					//original said bad path
					throw new AAIException("AAI_3001", "invalid item found in path: " + part);
				}
			}
		} catch (AAIException e) {
			throw new AAIException("AAI_" + e.getErrorObject().getErrorCode(), e.getErrorObject().getDetails());

		} catch (Exception e) {

			throw new AAIException("AAI_3001", e);

		}
	}
	
	//public abstract Object getResult();
	
	/**
	 * Handle cloud region.
	 *
	 * @param action the action
	 * @param uri the uri
	 * @return the uri
	 */
//	protected URI handleCloudRegion(String action, URI uri) {
//		
//		if (action.equals("add")) {
//			return this.cloudRegionWorkaround.addToUri(uri);
//		} else if (action.equals("remove")) {
//			return this.cloudRegionWorkaround.removeFromUri(uri);
//		} else {
//			return uri;
//		}
//	}
	
	/**
	 * Trim URI.
	 *
	 * @param uri the uri
	 * @return the uri
	 */
	protected URI trimURI(URI uri) {
		
		String result = uri.getRawPath();
		if (result.startsWith("/")) {
			result = result.substring(1, result.length());
		}
		
		if (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		
		result = result.replaceFirst("aai/v\\d+/", "");
		URI uriResult = UriBuilder.fromPath(result).build();
		return uriResult;
	}

}
