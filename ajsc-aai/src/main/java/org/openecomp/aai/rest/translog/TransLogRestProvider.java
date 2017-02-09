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

package org.openecomp.aai.rest.translog;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.openecomp.aai.domain.translog.TransactionLogEntries;
import org.openecomp.aai.domain.translog.TransactionLogEntry;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.rest.RESTAPI;
import org.openecomp.aai.rest.RESTAPI.Action;
import org.openecomp.aai.rest.search.SearchProvider;
import org.openecomp.aai.util.AAITxnLog;


/**
 * The Class TransLogRestProvider.
 */
@Path("/{parameter: v[8]}/translog")
public class TransLogRestProvider extends RESTAPI  {
	
	protected static String authPolicyFunctionName = "util";
	
	public static final String TRANSLOG_GET_ID = "/get/{id}";

	public static final String TRANSLOG_SCAN = "/scan";
	
	/**
	 * Gets the hbase trans.
	 *
	 * @param headers the headers
	 * @param req the req
	 * @param id the id
	 * @return the hbase trans
	 */
	@GET
	@Produces( { MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path(TRANSLOG_GET_ID)
	public Response getHbaseTrans(@Context HttpHeaders headers, 
									@Context HttpServletRequest req,
									@PathParam("id") String id) {
		AAIException ex = null;
		Response response = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		ArrayList<String> templateVars = new ArrayList<String>();		
		try {
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
			logline.init(COMPONENT, transId, fromAppId, "getHbaseTrans");
			txn = new AAITxnLog(transId, fromAppId);
			TransactionLogEntry txObj = txn.get(id);
			if (txObj.getTransactionLogEntryId() == null || txObj.getTransactionLogEntryId() == "")
				throw new AAIException("AAI_3001");
			response =  Response.ok(txObj).type(getMediaType(headers.getAcceptableMediaTypes())).build();

		} catch (AAIException e) { 
			// send error response
			ex = e;
			templateVars.add("GET translog");
			templateVars.add("id=" + id);
			response =  Response
						.status(e.getErrorObject().getHTTPResponseCode())
						.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
						.build();
		} catch (Exception e) {
			// send error response
			ex = new AAIException("AAI_4000", e);
			templateVars.add("GET translog");
			templateVars.add("id=" + id);
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
	 * Scan hbase transactions.
	 *
	 * @param headers the headers
	 * @param req the req
	 * @param startTime the start time
	 * @param endTime the end time
	 * @param methods the methods
	 * @param getFilter the get filter
	 * @param putFilter the put filter
	 * @param resourceFilter the resource filter
	 * @param fromAppIdFilter the from app id filter
	 * @return the response
	 */
	@GET
	@Produces( { MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path(TRANSLOG_SCAN)
	public Response scanHbaseTransactions(@Context HttpHeaders headers,
			@Context HttpServletRequest req,
			@QueryParam("startTime") String startTime,
			@QueryParam("endTime") String endTime,
			@QueryParam("methods") String methods,
			@QueryParam("getFilter") String getFilter,
			@QueryParam("putFilter") String putFilter,
			@QueryParam("resourceFilter") String resourceFilter,
			@QueryParam("fromAppIdFilter") String fromAppIdFilter
			) { 
		String sb = new String();
		TransactionLogEntries txs = null;
		AAIException ex = null;
		Response response = null;
		String fromAppId = null;
		String transId = null;
		LogLine logline = new LogLine();
		ArrayList<String> templateVars = new ArrayList<String>();
		try {
			fromAppId = getFromAppId(headers, logline );
			transId = getTransId(headers, logline);
			logline.init(COMPONENT, fromAppId, transId, "scanHbaseTransactions");
			
			long now = System.currentTimeMillis();
			long then = now - 600 * 1000;  // 10 minutes, maybe make it configurable?
			
			Date start = new Date(then);
			Date end = new Date(now);
			
			if (startTime != null) { 
				if (startTime.length() == 8) {
					startTime = startTime + "T000000";
				} else if (startTime.length() == 11) {
					startTime = startTime + "0000";
				} else if (startTime.length() == 13) { 
					startTime = startTime + "00";
				}
				start = getDateFromString(startTime);
			}

			if (endTime != null) { 
				if (endTime.length() == 8) {
					endTime = endTime + "T235959";
				} else if (endTime.length() == 11) {
					endTime = endTime + "5959";
				} else if (endTime.length() == 13) { 
					endTime = endTime + "59";
				}
				end = getDateFromString(endTime);
			}
			// should probably gripe about invalid dates from the URI
			long endMillis = end.getTime();
			long startMillis = start.getTime();
			
			List<String> methodList = new ArrayList<String>();
			
			if (methods != null) {
				for (String ent : methods.split(",")) { 
					methodList.add(ent);
				}
			} else { 
				methodList.add("PUT");
			}
			txn = new AAITxnLog(transId, fromAppId);
			txs = txn.scanFiltered(startMillis, endMillis, methodList, putFilter, getFilter, resourceFilter, fromAppIdFilter);
		
		if (txs == null) 
			throw new AAIException("AAI_3001", sb); 
		response =  Response.ok(txs).type(getMediaType(headers.getAcceptableMediaTypes())).build();
		
	} catch (AAIException e) { 
		// send error response
		ex = e;
		templateVars.add("GET translog");
		response =  Response
					.status(e.getErrorObject().getHTTPResponseCode())
					.entity(ErrorLogHelper.getRESTAPIErrorResponse(headers.getAcceptableMediaTypes(), e, templateVars, logline))
					.build();
	} catch (Exception e) {
		// send error response
		ex = new AAIException("AAI_4000", e);
		templateVars.add("GET translog");
		templateVars.add("scan=" + req.getQueryString());
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
	 * Gets the date from string.
	 *
	 * @param dateString the date string
	 * @return the date from string
	 */
	private Date getDateFromString(String dateString) {
	   Date date = null;
		try {
	        DateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
	        date = df.parse(dateString);
	        
	    } catch (ParseException e) {
	        //WebApplicationException ...("Date format should be yyyy-MM-dd'T'HH:mm:ss", Status.BAD_REQUEST);
	    }
	    return date;
	}
	// TODO: fix this to return one or more objects?
/*	@GET
	@Produces( { MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("/scan/{id}")
	public String scanHbaseTrans(@PathParam("id") String transId) {
		String sb = new String();
		try {
			List<String> logList = txn.scan(transId);
			for (String logValue : logList) {
				sb += "\n<Row>" + logValue + "\n</Row>";
			}
		} catch (Exception e) {
			sb = "hbase scan got error="+e.toString();
		} 
		return "<aai_hbaseScan_logresult>"
		+ "\n<input>"
		+ transId 
		+"\n</input>"
		+ "\n<Result>"
		+ sb
		+ "\n</Result>"
		+"\n</aai_hbaseScan_logresult>\n";

	}*/

}
