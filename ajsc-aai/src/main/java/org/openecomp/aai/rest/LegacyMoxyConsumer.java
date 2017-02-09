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

package org.openecomp.aai.rest;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.jaxrs.ext.PATCH;
import org.javatuples.Pair;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.parsers.query.QueryParser;
import org.openecomp.aai.rest.db.DBRequest;
import org.openecomp.aai.rest.db.HttpEntry;
import org.openecomp.aai.rest.util.ValidateEncoding;
import org.openecomp.aai.serialization.engines.QueryStyle;
import org.openecomp.aai.serialization.engines.TransactionalGraphEngine;
import org.openecomp.aai.workarounds.RemoveDME2QueryParams;

import com.google.common.base.Joiner;
import com.thinkaurelius.titan.core.TitanTransaction;


/**
 * The Class LegacyMoxyConsumer.
 */
@Path("{version: v[8]}")
public class LegacyMoxyConsumer extends RESTAPI {
	
	protected static AAILogger aaiLogger = new AAILogger(LegacyMoxyConsumer.class.getName());
	protected static String authPolicyFunctionName = "REST";
	private ModelType introspectorFactoryType = ModelType.MOXY;
	private QueryStyle queryStyle = QueryStyle.TRAVERSAL;
	
	/**
	 * Update.
	 *
	 * @param content the content
	 * @param versionParam the version param
	 * @param uri the uri
	 * @param headers the headers
	 * @param info the info
	 * @param req the req
	 * @return the response
	 */
	@PUT
	@Path("/{uri: .+}")
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response update (String content, @PathParam("version")String versionParam, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo info, @Context HttpServletRequest req) {
		
		MediaType mediaType = headers.getMediaType();

		return this.handleWrites(Action.PUT, mediaType, HttpMethod.PUT, content, versionParam, uri, headers, info, req);
	}
	
	/**
	 * Update relationship.
	 *
	 * @param content the content
	 * @param versionParam the version param
	 * @param uri the uri
	 * @param headers the headers
	 * @param info the info
	 * @param req the req
	 * @return the response
	 */
	@PUT
	@Path("/{uri: .+}/relationship-list/relationship")
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response updateRelationship (String content, @PathParam("version")String versionParam, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo info, @Context HttpServletRequest req) {
		
		
		
		String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");
		String transId = headers.getRequestHeaders().getFirst("X-TransactionId");

		MediaType inputMediaType = headers.getMediaType();

		Response response = null;
		Loader loader = null;
		TransactionalGraphEngine dbEngine = null;
		TitanTransaction g = null;
		boolean success = true;
		LogLineBuilder llBuilder = new LogLineBuilder(transId, sourceOfTruth);
		LogLine logline = llBuilder.build(COMPONENT,  HttpMethod.PUT_EDGE.toString());

   	 	try {
   			this.validateRequest(uri, headers, req, Action.PUT, info, logline);
   			Version version = Version.valueOf(versionParam);
   			version = Version.valueOf(versionParam);
			HttpEntry httpEntry = new HttpEntry(version, introspectorFactoryType, queryStyle, llBuilder);
			loader = httpEntry.getLoader();
			dbEngine = httpEntry.getDbEngine();
   			
   			g = dbEngine.getGraph().newTransaction();
   			
			URI uriObject = UriBuilder.fromPath(uri).build();

			QueryParser uriQuery = dbEngine.getQueryBuilder().createQueryFromURI(uriObject);
			
			Introspector wrappedEntity = loader.unmarshal("relationship", content, org.openecomp.aai.rest.MediaType.getEnum(this.getInputMediaType(inputMediaType)));
			
			DBRequest request = new DBRequest(HttpMethod.PUT_EDGE, uriObject, uriQuery, wrappedEntity, headers, info, transId);
			List<DBRequest> requests = new ArrayList<>();
			requests.add(request);
			Pair<Boolean, List<Pair<URI, Response>>> responsesTuple  = httpEntry.process(g, requests, sourceOfTruth);
	        
			response = responsesTuple.getValue1().get(0).getValue1();
			success = responsesTuple.getValue0();

		} catch (AAIException e) {
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.PUT, e, logline);
			success = false;
		} catch (Exception e) {
			AAIException aaiException = new AAIException("AAI_4000", e);
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.PUT, aaiException, logline);
			success = false;
		} finally {
			if (g != null) {
				if (success) {
					g.commit();
					aaiLogger.info(logline, true, "0");
				} else {
					g.rollback();
				}
			}

		}
		
		return response;
	}

	/**
	 * Patch.
	 *
	 * @param content the content
	 * @param versionParam the version param
	 * @param uri the uri
	 * @param headers the headers
	 * @param info the info
	 * @param req the req
	 * @return the response
	 */
	@PATCH
	@Path("/{uri: .+}")
	@Consumes({ "application/merge-patch+json" })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response patch (String content, @PathParam("version")String versionParam, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo info, @Context HttpServletRequest req) {
		
		MediaType mediaType = MediaType.APPLICATION_JSON_TYPE;
		
		return this.handleWrites(Action.PUT, mediaType, HttpMethod.MERGE_PATCH, content, versionParam, uri, headers, info, req);
	
	}
	
	/**
	 * Gets the legacy.
	 *
	 * @param content the content
	 * @param versionParam the version param
	 * @param uri the uri
	 * @param depthParam the depth param
	 * @param cleanUp the clean up
	 * @param headers the headers
	 * @param info the info
	 * @param req the req
	 * @return the legacy
	 */
	@GET
	@Path("/{uri: .+}")
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response getLegacy (String content, @PathParam("version")String versionParam, @PathParam("uri") @Encoded String uri, @DefaultValue("all") @QueryParam("depth") String depthParam, @DefaultValue("false") @QueryParam("cleanup") String cleanUp, @Context HttpHeaders headers, @Context UriInfo info, @Context HttpServletRequest req) {
		
		String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");
		String transId = headers.getRequestHeaders().getFirst("X-TransactionId");

		Response response = null;
		TransactionalGraphEngine dbEngine = null;
		TitanTransaction g = null;
		LogLineBuilder llBuilder = new LogLineBuilder(transId, sourceOfTruth);
		LogLine logline = llBuilder.build(COMPONENT,  HttpMethod.GET.toString());

		try {
			this.validateRequest(uri, headers, req, Action.GET, info, logline);
			Version version = Version.valueOf(versionParam);
			HttpEntry httpEntry = new HttpEntry(version, introspectorFactoryType, queryStyle, llBuilder);
			dbEngine = httpEntry.getDbEngine();
			
			
			MultivaluedMap<String, String> params = info.getQueryParameters();

			RemoveDME2QueryParams dme2Workaround = new RemoveDME2QueryParams();
			//clear out all params not used for filtering
			params.remove("depth");
			params.remove("cleanup");
			if (dme2Workaround.shouldRemoveQueryParams(params)) {
				dme2Workaround.removeQueryParams(params);
			}

			uri = uri.split("\\?")[0];
			
			URI uriObject = UriBuilder.fromPath(uri).build();

			QueryParser uriQuery = dbEngine.getQueryBuilder().createQueryFromURI(uriObject, params);
	        g = dbEngine.getGraph().newTransaction();

			DBRequest request = new DBRequest(HttpMethod.GET, uriObject, uriQuery, null, headers, info, transId);
			List<DBRequest> requests = new ArrayList<>();
			requests.add(request);
			Pair<Boolean, List<Pair<URI, Response>>> responsesTuple  = httpEntry.process(g, requests, sourceOfTruth);
	        
			response = responsesTuple.getValue1().get(0).getValue1();
			
		} catch (AAIException e) {
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.GET, e, logline);
		} catch (Exception e ) {
			AAIException ex = new AAIException("AAI_4000", e);
			
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.GET, ex, logline);
		} finally {
			if (g != null) {
				if (cleanUp.equals("true")) {
					g.commit();
				} else {
					g.rollback();
				}
				aaiLogger.info(logline, true, "0");
			}
		}
		
		return response;
	}

	/**
	 * Delete.
	 *
	 * @param versionParam the version param
	 * @param uri the uri
	 * @param headers the headers
	 * @param info the info
	 * @param resourceVersion the resource version
	 * @param req the req
	 * @return the response
	 */
	@DELETE
	@Path("/{uri: .+}")
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response delete (@PathParam("version")String versionParam, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo info, @QueryParam("resource-version")String resourceVersion, @Context HttpServletRequest req) {
		
		
		String outputMediaType = getMediaType(headers.getAcceptableMediaTypes());
		String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");
		String transId = headers.getRequestHeaders().getFirst("X-TransactionId");

		TransactionalGraphEngine dbEngine = null;
		Response response = Response.status(404)
				.type(outputMediaType).build();
				
		TitanTransaction g = null;
		boolean success = true;
		LogLineBuilder llBuilder = new LogLineBuilder(transId, sourceOfTruth);
		LogLine logline = llBuilder.build(COMPONENT,  HttpMethod.DELETE.toString());

		try {

			this.validateRequest(uri, headers, req, Action.DELETE, info, logline);
			Version version = Version.valueOf(versionParam);
			HttpEntry httpEntry = new HttpEntry(version, introspectorFactoryType, queryStyle, llBuilder);
			dbEngine = httpEntry.getDbEngine();
			
			g = dbEngine.getGraph().newTransaction();

			URI uriObject = UriBuilder.fromPath(uri).build();

			QueryParser uriQuery = dbEngine.getQueryBuilder().createQueryFromURI(uriObject);
			
			DBRequest request = new DBRequest(HttpMethod.DELETE, uriObject, uriQuery, null, headers, info, transId);
			List<DBRequest> requests = new ArrayList<>();
			requests.add(request);
			Pair<Boolean, List<Pair<URI, Response>>> responsesTuple  = httpEntry.process(g, requests, sourceOfTruth);
	        
			response = responsesTuple.getValue1().get(0).getValue1();
			success = responsesTuple.getValue0();

		} catch (AAIException e) {
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.DELETE, e, logline);
			success = false;
		} catch (Exception e) {
			AAIException ex = new AAIException("AAI_4000", e);
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.DELETE, ex, logline);
			success = false;
		} finally {
			if (g != null) {
				if (success) {
					g.commit();
					aaiLogger.info(logline, true, "0");
				} else {
					g.rollback();
				}
			}
		}
		
		return response;
	}
	
	/**
	 * This whole method does nothing because the body is being dropped while fielding the request.
	 *
	 * @param content the content
	 * @param versionParam the version param
	 * @param uri the uri
	 * @param headers the headers
	 * @param info the info
	 * @param req the req
	 * @return the response
	 */
	@DELETE
	@Path("/{uri: .+}/relationship-list/relationship")
	@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	public Response deleteRelationship (String content, @PathParam("version")String versionParam, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo info, @Context HttpServletRequest req) {		
		
		MediaType inputMediaType = headers.getMediaType();

		String outputMediaType = getMediaType(headers.getAcceptableMediaTypes());
		String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");
		String transId = headers.getRequestHeaders().getFirst("X-TransactionId");

		Loader loader = null;
		TransactionalGraphEngine dbEngine = null;
		Response response = Response.status(404)
				.type(outputMediaType).build();
	
		TitanTransaction g = null;
		boolean success = true;
		LogLineBuilder llBuilder = new LogLineBuilder(transId, sourceOfTruth);
		LogLine logline = llBuilder.build(COMPONENT,  HttpMethod.DELETE_EDGE.toString());

		try {
			this.validateRequest(uri, headers, req, Action.DELETE, info, logline);
			Version version = Version.valueOf(versionParam);
			HttpEntry httpEntry = new HttpEntry(version, introspectorFactoryType, queryStyle, llBuilder);
			loader = httpEntry.getLoader();
			dbEngine = httpEntry.getDbEngine();
			
			g = dbEngine.getGraph().newTransaction();

			if (content.equals("")) {
				throw new AAIException("AAI_3102", "You must supply a relationship");
			}
			URI uriObject = UriBuilder.fromPath(uri).build();
			
			QueryParser uriQuery = dbEngine.getQueryBuilder().createQueryFromURI(uriObject);
        				
			Introspector wrappedEntity = loader.unmarshal("relationship", content, org.openecomp.aai.rest.MediaType.getEnum(this.getInputMediaType(inputMediaType)));

    		DBRequest request = new DBRequest(HttpMethod.DELETE_EDGE, uriObject, uriQuery, wrappedEntity, headers, info, transId);
			List<DBRequest> requests = new ArrayList<>();
			requests.add(request);
			Pair<Boolean, List<Pair<URI, Response>>> responsesTuple  = httpEntry.process(g, requests, sourceOfTruth);
	        
			response = responsesTuple.getValue1().get(0).getValue1();
			success = responsesTuple.getValue0();
		} catch (AAIException e) {
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.DELETE, e, logline);
			success = false;
		} catch (Exception e) {
			AAIException ex = new AAIException("AAI_4000", e);
			response = consumerExceptionResponseGenerator(headers, info, HttpMethod.DELETE, ex, logline);
			success = false;
		} finally {
			if (g != null) {
				if (success) {
					g.commit();
					aaiLogger.info(logline, true, "0");
				} else {
					g.rollback();
				}
			}
		}
		
		return response;
	}
	
	/**
	 * Validate request.
	 *
	 * @param uri the uri
	 * @param headers the headers
	 * @param req the req
	 * @param action the action
	 * @param info the info
	 * @param logline the logline
	 * @throws AAIException the AAI exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 */
	private void validateRequest(String uri, HttpHeaders headers, HttpServletRequest req, Action action, UriInfo info, 
			 LogLine logline) throws AAIException, UnsupportedEncodingException {
		String namespace = "";
		String[] splitUri = uri.split("/");
		String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");
		String transId = headers.getRequestHeaders().getFirst("X-TransactionId");
		if (sourceOfTruth == null) {
			throw new AAIException("AAI_4009");
		}
		if (transId == null) {
			throw new AAIException("AAI_4010");
		}
		
		
		if (!ValidateEncoding.getInstance().validate(info)) {
			throw new AAIException("AAI_3008", "uri=" + getPath(info));
		}
		if (splitUri.length > 0) {
			namespace = splitUri[0];
		}
	}
	
	/**
	 * Gets the path.
	 *
	 * @param info the info
	 * @return the path
	 */
	private String getPath(UriInfo info) {
		String path = info.getPath(false);
		MultivaluedMap<String, String> map = info.getQueryParameters(false);
		String params = "?";
		List<String> parmList = new ArrayList<>();
		for (String key : map.keySet()) {
			for (String value : map.get(key)) {
				parmList.add(key + "=" + value);
			}
		}
		String queryParams = Joiner.on("&").join(parmList);
		if (map.keySet().size() > 0) {
			path += params + queryParams;
		}
		
		return path;
		
	}
	
	/**
	 * Handle writes.
	 *
	 * @param aaiAction the aai action
	 * @param mediaType the media type
	 * @param method the method
	 * @param content the content
	 * @param versionParam the version param
	 * @param uri the uri
	 * @param headers the headers
	 * @param info the info
	 * @param req the req
	 * @return the response
	 */
	private Response handleWrites(Action aaiAction, MediaType mediaType, HttpMethod method, String content, String versionParam, String uri, HttpHeaders headers, UriInfo info, HttpServletRequest req) {
		

		Response response = null;
		TransactionalGraphEngine dbEngine = null;
		Loader loader = null;
		Version version = null;
		String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");
		String transId = headers.getRequestHeaders().getFirst("X-TransactionId");

		TitanTransaction g = null;

		Boolean success = true;
		LogLineBuilder llBuilder = new LogLineBuilder(transId, sourceOfTruth);
		LogLine logline = llBuilder.build(COMPONENT,  method.toString());

   	 	try {
	
			this.validateRequest(uri, headers, req, Action.PUT, info, logline);

			version = Version.valueOf(versionParam);
			HttpEntry httpEntry = new HttpEntry(version, introspectorFactoryType, queryStyle, llBuilder);
			loader = httpEntry.getLoader();
			dbEngine = httpEntry.getDbEngine();
			
	        g = dbEngine.getGraph().newTransaction();

			URI uriObject = UriBuilder.fromPath(uri).build();

			QueryParser uriQuery = dbEngine.getQueryBuilder().createQueryFromURI(uriObject);
	        String objName = uriQuery.getResultType();
	        if (content.length() == 0 && mediaType.equals(MediaType.APPLICATION_JSON)) {
	        	content = "{}";
	        }
	        Introspector obj = loader.unmarshal(objName, content, org.openecomp.aai.rest.MediaType.getEnum(this.getInputMediaType(mediaType)));
	        if (obj == null) {
	        	throw new AAIException("AAI_3000", "object could not be unmarshalled:" + content);
	        }
	        
	        boolean validateRequired = true;
	        if (method.equals(HttpMethod.MERGE_PATCH)) {
	        	validateRequired = false;
	        }
	        this.validateIntrospector(obj, loader, uriObject, validateRequired);
	        
			DBRequest request = new DBRequest(method, uriObject, uriQuery, obj, headers, info, transId);
			request.setRawContent(content);
			List<DBRequest> requests = new ArrayList<>();
			requests.add(request);
			Pair<Boolean, List<Pair<URI, Response>>> responsesTuple  = httpEntry.process(g,  requests, sourceOfTruth);
	        
			response = responsesTuple.getValue1().get(0).getValue1();
			success = responsesTuple.getValue0();
		} catch (AAIException e) {
			response = consumerExceptionResponseGenerator(headers, info, method, e, logline);
			success = false;
		} catch (Exception e ) {
			AAIException ex = new AAIException("AAI_4000", e);
			response = consumerExceptionResponseGenerator(headers, info, method, ex, logline);
			success = false;
		} finally {
			if (g != null) {
				if (success) {
					g.commit();
					aaiLogger.info(logline, true, "0");
				} else {
					g.rollback();
				}
			}
		}
		
		return response;
	}
	
	/**
	 * Gets the input media type.
	 *
	 * @param mediaType the media type
	 * @return the input media type
	 */
	private String getInputMediaType(MediaType mediaType) {
		String result = mediaType.getType() + "/" + mediaType.getSubtype();
		
		return result;
		
	}

}
