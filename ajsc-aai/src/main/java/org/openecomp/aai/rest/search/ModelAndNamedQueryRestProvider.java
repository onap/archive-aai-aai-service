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
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.dbmap.SearchGraph;
import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
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

@Path("/search")
public class ModelAndNamedQueryRestProvider extends RESTAPI {
	
	protected static String authPolicyFunctionName = "search";
	
	protected static AAILogger aaiLogger = new AAILogger(ModelAndNamedQueryRestProvider.class.getName());

	public static final String NAMED_QUERY = "/named-query";
	
	public static final String MODEL_QUERY = "/model";
	
	/**
	 * Gets the named query response.
	 *
	 * @param headers the headers
	 * @param req the req
	 * @param queryParameters the query parameters
	 * @return the named query response
	 */
	/* ---------------- Start Named Query --------------------- */
	@POST
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(NAMED_QUERY)
	public Response getNamedQueryResponse(@Context HttpHeaders headers,
										@Context HttpServletRequest req,
										String queryParameters) {
		AAIException ex = null;
		Response response = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		String rqstTm = genDate(logline);
		ArrayList<String> templateVars = new ArrayList<String>();	
		try { 
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
			
			logline.init(COMPONENT, transId, fromAppId, "getNamedQueryResponse");
			
			AAIExtensionMap aaiExtMap = new AAIExtensionMap();						
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			aaiExtMap.setApiVersion(AAIApiVersion.get());
			
			SearchGraph searchGraph = new SearchGraph();
			response = searchGraph.runNamedQuery(fromAppId, transId, queryParameters, aaiExtMap);
	
			String respTm = genDate();
			logTransaction(fromAppId, transId, "GETSDNZONERESPONSE",
					req.getRequestURI(), rqstTm, respTm, "", response, logline);

		} catch (AAIException e) {
			// send error response
			ex = e;
			templateVars.add("POST Search");
			templateVars.add("getNamedQueryResponse");
			response =  Response
						.status(e.getErrorObject().getHTTPResponseCode())
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
						.build();
		} catch (Exception e) {
			// send error response
			ex = new AAIException("AAI_4000", e);
			templateVars.add("POST Search");
			templateVars.add("getNamedQueryResponse");
			response = Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex, templateVars, logline))
						.build();
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(logline, true, "0");
			else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		return response;
	}
	
	/**
	 * Gets the model query response.
	 *
	 * @param headers the headers
	 * @param req the req
	 * @param inboundPayload the inbound payload
	 * @param action the action
	 * @return the model query response
	 */
	/* ---------------- Start Named Query --------------------- */
	@POST
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
	@Path(MODEL_QUERY)
	public Response getModelQueryResponse(@Context HttpHeaders headers,
										@Context HttpServletRequest req,
										String inboundPayload,
										@QueryParam("action") String action) {
		AAIException ex = null;
		Response response = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		String rqstTm = genDate(logline);
		ArrayList<String> templateVars = new ArrayList<String>();	
		try { 
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
			
			logline.init(COMPONENT, transId, fromAppId, "getNamedQueryResponse");
			
			AAIExtensionMap aaiExtMap = new AAIExtensionMap();						
			aaiExtMap.setHttpHeaders(headers);
			aaiExtMap.setServletRequest(req);
			aaiExtMap.setApiVersion(AAIApiVersion.get());
			aaiExtMap.setFromAppId(fromAppId);
			aaiExtMap.setTransId(transId);
			SearchGraph searchGraph = new SearchGraph();
			if (action != null && action.equalsIgnoreCase("DELETE")) { 
				response = searchGraph.executeModelOperation(fromAppId, transId, inboundPayload, true, aaiExtMap);
			} else { 
				response = searchGraph.executeModelOperation(fromAppId, transId, inboundPayload, false, aaiExtMap);
			}
			String respTm = genDate();
			logTransaction(fromAppId, transId, "POSTMODELQUERYRESPONSE",
					req.getRequestURI(), rqstTm, respTm, "", response, logline);
			
		} catch (AAIException e) {
			// send error response
			ex = e;
			templateVars.add("POST Search");
			templateVars.add("getModelQueryResponse");
			response =  Response
						.status(e.getErrorObject().getHTTPResponseCode())
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
						.build();
		} catch (Exception e) {
			// send error response
			ex = new AAIException("AAI_4000", e);
			templateVars.add("POST Search");
			templateVars.add("getModelQueryResponse");
			response = Response
						.status(Status.INTERNAL_SERVER_ERROR)
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), ex, templateVars, logline))
						.build();
		} finally {
			// log success or failure
			if (ex == null)
				aaiLogger.info(logline, true, "0");
			else {
				aaiLogger.error(ex.getErrorObject(), logline, ex);
				aaiLogger.info(logline, false, ex.getErrorObject().getErrorCodeString());
			}
		}
		return response;
	}

}
