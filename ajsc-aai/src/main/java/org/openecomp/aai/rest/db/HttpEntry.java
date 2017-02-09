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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.javatuples.Pair;
import org.openecomp.aai.domain.responseMessage.AAIResponseMessage;
import org.openecomp.aai.domain.responseMessage.AAIResponseMessageDatum;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.extensions.ExtensionController;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.MarshallerProperties;
import org.openecomp.aai.introspection.ModelInjestor;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.parsers.query.QueryParser;
import org.openecomp.aai.parsers.uri.URIToExtensionInformation;
import org.openecomp.aai.rest.HttpMethod;
import org.openecomp.aai.rest.ueb.UEBNotification;
import org.openecomp.aai.serialization.db.DBSerializer;
import org.openecomp.aai.serialization.engines.QueryStyle;
import org.openecomp.aai.serialization.engines.TitanDBEngine;
import org.openecomp.aai.serialization.engines.TransactionalGraphEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import com.thinkaurelius.titan.core.TitanTransaction;

/**
 * The Class HttpEntry.
 */
public class HttpEntry {

	private final ModelType introspectorFactoryType;
	
	private final QueryStyle queryStyle;
	
	private final Version version;
	
	private final Loader loader;
	
	private final TransactionalGraphEngine dbEngine;
	
	private final LogLineBuilder llBuilder;
	
	private boolean processSingle = true;
	
	protected static AAILogger aaiLogger = new AAILogger(HttpEntry.class.getName());
	
	/**
	 * Instantiates a new http entry.
	 *
	 * @param version the version
	 * @param modelType the model type
	 * @param queryStyle the query style
	 * @param llBuilder the ll builder
	 */
	public HttpEntry(Version version, ModelType modelType, QueryStyle queryStyle, LogLineBuilder llBuilder) {
		this.introspectorFactoryType = modelType;
		this.queryStyle = queryStyle;
		this.version = version;
		this.llBuilder = llBuilder;
		this.loader = LoaderFactory.createLoaderForVersion(introspectorFactoryType, version, llBuilder);
		this.dbEngine = new TitanDBEngine(
				queryStyle,
				loader);
		
		
	}
	
	/**
	 * Gets the introspector factory type.
	 *
	 * @return the introspector factory type
	 */
	public ModelType getIntrospectorFactoryType() {
		return introspectorFactoryType;
	}

	/**
	 * Gets the query style.
	 *
	 * @return the query style
	 */
	public QueryStyle getQueryStyle() {
		return queryStyle;
	}

	/**
	 * Gets the version.
	 *
	 * @return the version
	 */
	public Version getVersion() {
		return version;
	}

	/**
	 * Gets the loader.
	 *
	 * @return the loader
	 */
	public Loader getLoader() {
		return loader;
	}

	/**
	 * Gets the db engine.
	 *
	 * @return the db engine
	 */
	public TransactionalGraphEngine getDbEngine() {
		return dbEngine;
	}

	/**
	 * Process.
	 *
	 * @param g the g
	 * @param requests the requests
	 * @param sourceOfTruth the source of truth
	 * @return the pair
	 * @throws AAIException the AAI exception
	 */
	public Pair<Boolean, List<Pair<URI, Response>>> process (TitanTransaction g, List<DBRequest> requests, String sourceOfTruth) throws AAIException {
		DBSerializer serializer = new DBSerializer(version, dbEngine, g, introspectorFactoryType, sourceOfTruth, llBuilder);
		Response response = null;
		Status status = Status.NOT_FOUND;
		Introspector obj = null;
		QueryParser query = null;
		URI uri = null;
		UEBNotification	notification = new UEBNotification(loader);
		int depth = Integer.MAX_VALUE;
		List<Pair<URI,Response>> responses = new ArrayList<>();
		MultivaluedMap<String, String> params = null;
		HttpMethod method = null;
		String uriTemp = "";
		Boolean success = true;
		for (DBRequest request : requests) {
			try {
				method = request.getMethod();
				obj = request.getIntrospector();
				query = request.getParser();
				uriTemp = request.getUri().getRawPath().replaceFirst("^v\\d+/", "");
				uri = UriBuilder.fromPath(uriTemp).build();
				List<Vertex> vertices = dbEngine.getQueryEngine().executeQuery(g, query.getQueryBuilder());
				boolean isNewVertex = false;
				String outputMediaType = getMediaType(request.getHeaders().getAcceptableMediaTypes());
				String result = null;
				params = request.getInfo().getQueryParameters(false);
				depth = setDepth(params.getFirst("depth"));
				String cleanUp = params.getFirst("cleanup");
				if (cleanUp == null) {
					cleanUp = "false";
				}
				if (vertices.size() > 1 && processSingle && !method.equals(HttpMethod.GET)) {
					if (method.equals(HttpMethod.DELETE)) {
						throw new AAIException("AAI_6138");
					} else {
						throw new AAIException("AAI_6137");
					}
				}
				if (method.equals(HttpMethod.PUT) || method.equals(HttpMethod.MERGE_PATCH)) {
					String resourceVersion = (String)obj.getValue("resource-version");
					if (vertices.size() == 1) {
						serializer.verifyResourceVersion("update", query.getResultType(), (String)vertices.get(0).<String>property("resource-version").orElse(null), resourceVersion, obj.getURI());
						isNewVertex = false;
					} else {
						serializer.verifyResourceVersion("create", query.getResultType(), "", resourceVersion, obj.getURI());
						isNewVertex = true;
					}
				} else {
					if (vertices.size() == 0) {
						String msg = createNotFoundMessage(query.getResultType(), request.getUri());
						throw new AAIException("AAI_6114", msg);
					} else {
						isNewVertex = false;
					}
				}
				Vertex v = null;
				if (isNewVertex) {
					v = serializer.createNewVertex(obj);
				} else {
					v = vertices.get(0);
					if (isModificationMethod(method)) {
						serializer.touchStandardVertexProperties(v, false);
					}
				}
				HashMap<String, Introspector> relatedObjects = new HashMap<>();
				switch (method) {
					case GET:
						obj = this.getObjectFromDb(serializer, g, query, obj, request.getUri(), depth, cleanUp);
						if (obj != null) {
							status = Status.OK;
							MarshallerProperties properties = 
									new MarshallerProperties.Builder(org.openecomp.aai.rest.MediaType.getEnum(outputMediaType)).build();
							result = obj.marshal(properties);
						}
						
						break;
					case PUT:
						response = this.invokeExtension(dbEngine, g, method, request.getTransactionId(), sourceOfTruth, version, loader, obj, uri, request.getHeaders(), true);
						serializer.serializeToDb(obj, v, query, uri.getRawPath());
						this.invokeExtension(dbEngine, g, HttpMethod.PUT, request.getTransactionId(), sourceOfTruth, version, loader, obj, uri, request.getHeaders(), false);
						status = Status.OK;
						if (isNewVertex) {
							status = Status.CREATED;
						}
						obj = serializer.getLatestVersionView(v);
						if (query.isDependent()) {
							relatedObjects = this.getRelatedObjects(serializer, v);
						}
						notification.createNotificationEvent(sourceOfTruth, status, uri, obj, relatedObjects);
						
						break;
					case PUT_EDGE:
						serializer.createEdge(obj, v);
						status = Status.OK;
						break;
					case MERGE_PATCH:
						Introspector existingObj = (Introspector) obj.clone();
						existingObj = this.getObjectFromDb(serializer, g, query, existingObj, request.getUri(), depth, cleanUp);
						String existingJson = existingObj.marshal(false);
						String newJson = request.getRawContent();
						ObjectMapper mapper = new ObjectMapper();
						try {
							JsonNode existingNode = mapper.readTree(existingJson);
							JsonNode newNode = mapper.readTree(newJson);
							JsonMergePatch patch = JsonMergePatch.fromJson(newNode);
							JsonNode completed = patch.apply(existingNode);
							String patched = mapper.writeValueAsString(completed);
							Introspector patchedObj = loader.unmarshal(existingObj.getName(), patched);
							serializer.serializeToDb(patchedObj, v, query, uri.getRawPath());
							status = Status.OK;
							patchedObj = serializer.getLatestVersionView(v);
							if (query.isDependent()) {
								relatedObjects = this.getRelatedObjects(serializer, v);
							}
							notification.createNotificationEvent(sourceOfTruth, status, uri, patchedObj, relatedObjects);
						} catch (IOException | JsonPatchException e) {
							throw new AAIException("AAI_3000", "could not perform patch operation");
						}
						break;
					case DELETE:
						String resourceVersion = params.getFirst("resource-version");
						obj = serializer.getLatestVersionView(v);
						if (query.isDependent()) {
							relatedObjects = this.getRelatedObjects(serializer, v);
						}
						this.invokeExtension(dbEngine, g, method, request.getTransactionId(), sourceOfTruth, version, loader, obj, uri, request.getHeaders(), true);
						serializer.delete(v, resourceVersion);
						this.invokeExtension(dbEngine, g, method, request.getTransactionId(), sourceOfTruth, version, loader, obj, uri, request.getHeaders(), false);
						status = Status.NO_CONTENT;
						notification.createNotificationEvent(sourceOfTruth, status, uri, obj, relatedObjects);
						break;
					case DELETE_EDGE:
						serializer.deleteEdge(obj, v);
						status = Status.NO_CONTENT;
						break;
					default:
						break;
				}
				
				
				/* temporarily adding vertex id to the headers
				 * to be able to use for testing the vertex id endpoint functionality
				 * since we presently have no other way of generating those id urls
				*/
				if (response == null && (method.equals(HttpMethod.PUT) || method.equals(HttpMethod.GET))) {
					List<Vertex> results = dbEngine.getQueryEngine().executeQuery(g, query.getQueryBuilder());
					Vertex myvert = results.get(0);
					String myvertid = myvert.id().toString();
					response = Response.status(status)
							.header("vertex-id", myvertid)
							.entity(result)
							.type(outputMediaType).build();
				} else if (response == null) {
					response = Response.status(status)
							.type(outputMediaType).build();
				} else {
					//response already set to something
				}
				Pair<URI,Response> pairedResp = Pair.with(request.getUri(), response);
				responses.add(pairedResp);
			} catch (AAIException e) {
				success = false;
				ArrayList<String> templateVars = new ArrayList<String>();
				templateVars.add(request.getMethod().toString()); //GET, PUT, etc
				templateVars.add(request.getUri().getPath().toString());
				templateVars.addAll(e.getTemplateVars());
				LogLine logline = new LogLine();
				response = Response
						.status(e.getErrorObject().getHTTPResponseCode())
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(request.getHeaders().getAcceptableMediaTypes(), e, templateVars, logline))
						.build();
				Pair<URI,Response> pairedResp = Pair.with(request.getUri(), response);
				responses.add(pairedResp);
				continue;
			} catch (Exception e) {
				success = false;
				AAIException ex = new AAIException("AAI_4000", e);
				ArrayList<String> templateVars = new ArrayList<String>();
				templateVars.add(request.getMethod().toString()); //GET, PUT, etc
				templateVars.add(request.getUri().getPath().toString());
				LogLine logline = new LogLine();
				response = Response
						.status(ex.getErrorObject().getHTTPResponseCode())
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(request.getHeaders().getAcceptableMediaTypes(), ex, templateVars, logline))
						.build();
				Pair<URI, Response> pairedResp = Pair.with(request.getUri(), response);
				responses.add(pairedResp);
				continue;
			}
		}
		
		notification.triggerEvents();
		Pair<Boolean, List<Pair<URI, Response>>> tuple = Pair.with(success, responses);
		return tuple;
	}
	
	/**
	 * Gets the media type.
	 *
	 * @param mediaTypeList the media type list
	 * @return the media type
	 */
	private String getMediaType(List <MediaType> mediaTypeList) {
		String mediaType = MediaType.APPLICATION_JSON;  // json is the default    
		for (MediaType mt : mediaTypeList) {
			if (MediaType.APPLICATION_XML_TYPE.isCompatible(mt)) {
				mediaType = MediaType.APPLICATION_XML;
			} 
		}
		return mediaType;
	}
	
	/**
	 * Gets the object from db.
	 *
	 * @param serializer the serializer
	 * @param g the g
	 * @param query the query
	 * @param obj the obj
	 * @param uri the uri
	 * @param depth the depth
	 * @param cleanUp the clean up
	 * @return the object from db
	 * @throws AAIException the AAI exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws SecurityException the security exception
	 * @throws InstantiationException the instantiation exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws MalformedURLException the malformed URL exception
	 */
	private Introspector getObjectFromDb(DBSerializer serializer, Graph g, QueryParser query, Introspector obj, URI uri, int depth, String cleanUp) throws AAIException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException, NoSuchMethodException, UnsupportedEncodingException, MalformedURLException {
        String objType = "";
        if (!query.getContainerType().equals("")) {
        	objType = query.getContainerType();
        } else {
        	objType = query.getResultType();
        }
        
        obj = loader.introspectorFromName(objType);
        List<Vertex> results = dbEngine.getQueryEngine().executeQuery(g, query.getQueryBuilder());
        //nothing found
        if (results.size() == 0) {
        	String msg = createNotFoundMessage(query.getResultType(), uri);
			throw new AAIException("AAI_6114", msg);
        }

        obj = serializer.dbToObject(results, obj, depth, cleanUp);
        return obj;
	}
	
	/**
	 * Invoke extension.
	 *
	 * @param dbEngine the db engine
	 * @param g the g
	 * @param httpMethod the http method
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param apiVersion the api version
	 * @param loader the loader
	 * @param obj the obj
	 * @param uri the uri
	 * @param headers the headers
	 * @param isPreprocess the is preprocess
	 * @return the response
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	private Response invokeExtension(TransactionalGraphEngine dbEngine, TitanTransaction g, HttpMethod httpMethod, String transId, String fromAppId, Version apiVersion, Loader loader, Introspector obj, URI uri, HttpHeaders headers, boolean isPreprocess) throws IllegalArgumentException, UnsupportedEncodingException, AAIException {
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		ModelInjestor injestor = ModelInjestor.getInstance();
		Response response = null;
		URIToExtensionInformation extensionInformation = new URIToExtensionInformation(loader, uri);
		aaiExtMap.setTransId(transId);
		aaiExtMap.setFromAppId(fromAppId);
		aaiExtMap.setAaiLogger(aaiLogger);
		aaiExtMap.setLogline(new LogLine());
		aaiExtMap.setGraph(g);
		aaiExtMap.setApiVersion(apiVersion.toString());
		aaiExtMap.setObjectFromRequest(obj.getUnderlyingObject());
		aaiExtMap.setObjectFromRequestType(obj.getJavaClassName());
		aaiExtMap.setObjectFromResponse(obj.getUnderlyingObject());
		aaiExtMap.setObjectFromResponseType(obj.getJavaClassName());
		aaiExtMap.setJaxbContext(injestor.getContextForVersion(apiVersion));
		aaiExtMap.setUri(uri.getRawPath());
		aaiExtMap.setTransactionalGraphEngine(dbEngine);
		aaiExtMap.setLoader(loader);
		aaiExtMap.setNamespace(extensionInformation.getNamespace());

		ExtensionController ext = new ExtensionController();
		ext.runExtension(aaiExtMap.getApiVersion(),
				extensionInformation.getNamespace(),
				extensionInformation.getTopObject(),
				extensionInformation.getMethodName(httpMethod, isPreprocess),
				aaiExtMap,
				isPreprocess);
		
		if (aaiExtMap.getPrecheckAddedList().size() > 0) {
			response = notifyOnSkeletonCreation(aaiExtMap, obj, headers);
		}
		
		return response;
	}
	
	/**
	 * Notify on skeleton creation.
	 *
	 * @param aaiExtMap the aai ext map
	 * @param input the input
	 * @param headers the headers
	 * @return the response
	 */
	//Legacy support
	private Response notifyOnSkeletonCreation(AAIExtensionMap aaiExtMap, Introspector input, HttpHeaders headers) {
		Response response = null;
		LogLine logline = new LogLine();
		HashMap<AAIException, ArrayList<String>> exceptionList = new HashMap<AAIException, ArrayList<String>>();

		String keyString = "";

		List<String> resourceKeys = input.getKeys();
		for (String key : resourceKeys) { 
			keyString += key + "=" + input.getValue(key) + " ";
		}

		for (AAIResponseMessage msg : aaiExtMap.getPrecheckResponseMessages().getAAIResponseMessage()) {
			ArrayList<String> templateVars = new ArrayList<String>();

			templateVars.add("PUT " + input.getDbName());
			templateVars.add(keyString);
			List<String> keys = new ArrayList<String>();
			templateVars.add(msg.getAaiResponseMessageResourceType());
			for (AAIResponseMessageDatum dat : msg.getAaiResponseMessageData().getAAIResponseMessageDatum()) {
				keys.add(dat.getAaiResponseMessageDatumKey() + "=" + dat.getAaiResponseMessageDatumValue());
			}
			templateVars.add(StringUtils.join(keys, ", "));
			exceptionList.put(new AAIException("AAI_0004", msg.getAaiResponseMessageResourceType()),
					templateVars);
		}
		response = Response
				.status(Status.ACCEPTED).entity(ErrorLogHelper
						.getRESTAPIInfoResponse(headers.getAcceptableMediaTypes(), exceptionList, logline))
						.build();
		
		return response;
	}
	
	/**
	 * Creates the not found message.
	 *
	 * @param resultType the result type
	 * @param uri the uri
	 * @return the string
	 */
	private String createNotFoundMessage(String resultType, URI uri) {
		
    	String msg = "No Node of type " + resultType + " found at: " + uri.getPath();

    	return msg;
	}
	
	/**
	 * Sets the depth.
	 *
	 * @param depthParam the depth param
	 * @return the int
	 * @throws AAIException the AAI exception
	 */
	protected int setDepth(String depthParam) throws AAIException {
		int depth = Integer.MAX_VALUE; //default 
		if (depthParam != null && depthParam.length() > 0 && !depthParam.equals("all")){
			try {
				depth = Integer.valueOf(depthParam);
			} catch (Exception e) {
				throw new AAIException("AAI_4016");
			}
		}
		return depth;
	}
	
	/**
	 * Checks if is modification method.
	 *
	 * @param method the method
	 * @return true, if is modification method
	 */
	private boolean isModificationMethod(HttpMethod method) {
		boolean result = false;
		
		if (method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PUT_EDGE) || method.equals(HttpMethod.DELETE_EDGE)) {
			result = true;
		}
		
		return result;
		
	}
	
	private HashMap<String, Introspector> getRelatedObjects(DBSerializer serializer, Vertex v) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, InstantiationException, NoSuchMethodException, UnsupportedEncodingException, MalformedURLException, AAIException {
		HashMap<String, Introspector> relatedVertices = new HashMap<>();
		List<Vertex> vertexChain = dbEngine.getQueryEngine().findParents(v);
		Introspector vertexObj = null;
		for (Vertex vertex : vertexChain) {
			vertexObj = serializer.getVertexProperties(vertex);
			relatedVertices.put(vertexObj.getObjectId(), vertexObj);
			
		}
		
		return relatedVertices;
	}
 	
}
