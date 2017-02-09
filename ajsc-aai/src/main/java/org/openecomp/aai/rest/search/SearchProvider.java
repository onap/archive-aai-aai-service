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

package org.openecomp.aai.rest.search;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.openecomp.aai.dbmap.SearchGraph;
import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.rest.RESTAPI;
import org.openecomp.aai.util.AAIApiVersion;
import org.openecomp.aai.domain.yang.SearchResults;

/**
 * Implements the search subdomain in the REST API. All API calls must include
 * X-FromAppId and X-TransactionId in the header.
 * 
 
 *
 */

@Path("/{parameter: v[8]}/search")
public class SearchProvider extends RESTAPI {
	
	protected static String authPolicyFunctionName = "search";
	
	protected static AAILogger aaiLogger = new AAILogger(SearchProvider.class.getName());


	public static final String GENERIC_QUERY = "/generic-query";

	public static final String NODES_QUERY = "/nodes-query";



	/**
	 * Gets the generic query response.
	 *
	 * @param headers the headers
	 * @param req the req
	 * @param startNodeType the start node type
	 * @param startNodeKeyParams the start node key params
	 * @param includeNodeTypes the include node types
	 * @param depth the depth
	 * @return the generic query response
	 */
	/* ---------------- Start Generic Query --------------------- */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(GENERIC_QUERY)
	public Response getGenericQueryResponse(@Context HttpHeaders headers,
											@Context HttpServletRequest req,
											@QueryParam("start-node-type") final String startNodeType,
											@QueryParam("key") final List<String> startNodeKeyParams,
											@QueryParam("include") final List<String> includeNodeTypes,
											@QueryParam("depth") final int depth) {
		
		AAIException ex = null;
		Response searchResult = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		String rqstTm = genDate(logline);
		ArrayList<String> templateVars = new ArrayList<String>();
		try { 
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
			
			logline.init(COMPONENT, transId, fromAppId, "getGenericQueryResponse");
			
			AAIExtensionMap aaiExtMap = new AAIExtensionMap();						
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			aaiExtMap.setApiVersion(AAIApiVersion.get());
			
			SearchGraph searchGraph = new SearchGraph();
			searchResult = searchGraph.runGenericQuery(fromAppId,
													   transId, 
													   startNodeType, 
													   startNodeKeyParams,
													   includeNodeTypes, 
													   depth, 
													   aaiExtMap);
	
			String respTm = genDate();
			logTransaction(fromAppId, transId,
					"GETGENERICQUERYRESPONSE", req.getRequestURI(), rqstTm, respTm,
					"", searchResult, logline);
		
		} catch (AAIException e) { 
			// send error response
			ex = e;
			templateVars.add("GET Search");
			templateVars.add("getGenericQueryResponse");
			searchResult =  Response
							.status(e.getErrorObject().getHTTPResponseCode())
							.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
							.build();
		} catch (Exception e) {
			// send error response
			ex = new AAIException("AAI_4000", e);
			templateVars.add("GET Search");
			templateVars.add("getGenericQueryResponse");
			searchResult = Response
							.status(Status.INTERNAL_SERVER_ERROR)
							.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex, templateVars, logline))
							.build();
		} finally {
			// log success or failure
			if (ex == null){
				aaiLogger.info(logline, true, "0");
			}
			else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}

		return searchResult;
	}

	/* ---------------- End Generic Query --------------------- */

	/**
	 * Gets the nodes query response.
	 *
	 * @param headers the headers
	 * @param req the req
	 * @param searchNodeType the search node type
	 * @param edgeFilterList the edge filter list
	 * @param filterList the filter list
	 * @return the nodes query response
	 */
	/* ---------------- Start Nodes Query --------------------- */
	@GET
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(NODES_QUERY)
	public Response getNodesQueryResponse(@Context HttpHeaders headers,
											@Context HttpServletRequest req,
											@QueryParam("search-node-type") final String searchNodeType,
											@QueryParam("edge-filter") final List<String> edgeFilterList, 
											@QueryParam("filter") final List<String> filterList) {
		AAIException ex = null;
		Response searchResult = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		String rqstTm = genDate(logline);
		ArrayList<String> templateVars = new ArrayList<String>();	
		try { 
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
			
			logline.init(COMPONENT, transId, fromAppId, "getNodesQueryResponse");

			AAIExtensionMap aaiExtMap = new AAIExtensionMap();						
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			aaiExtMap.setApiVersion(AAIApiVersion.get());
			
			SearchGraph searchGraph = new SearchGraph();
			searchResult = searchGraph.runNodesQuery(fromAppId,
													transId, 
													searchNodeType, 
													edgeFilterList, 
													filterList,
													aaiExtMap);
	
			String respTm = genDate();
			logTransaction(fromAppId, transId, "GETNODESQUERYRESPONSE",
					req.getRequestURI(), rqstTm, respTm, "", searchResult, logline);
		} catch (AAIException e) { 
			// send error response
			ex = e;
			templateVars.add("GET Search");
			templateVars.add("getNodesQueryResponse");
			searchResult =  Response
							.status(e.getErrorObject().getHTTPResponseCode())
							.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
							.build();
		} catch (Exception e) {
			// send error response
			ex = new AAIException("AAI_4000", e);
			templateVars.add("GET Search");
			templateVars.add("getNodesQueryResponse");
			searchResult = Response
							.status(Status.INTERNAL_SERVER_ERROR)
							.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex, templateVars, logline))
							.build();
		} finally {
			// log success or failure
			if (ex == null){
				aaiLogger.info(logline, true, "0");
			}
			else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		return searchResult;
	}

	/* ---------------- End Nodes Query --------------------- */
	
	
}
