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

package org.openecomp.aai.util;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.codehaus.groovy.util.SingleKeyHashMap;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.RestObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class RestController {

	private static AAILogger aaiLogger = new AAILogger(RestController.class.getName());
	private static LogLine logline = new LogLine();
	private static final String COMPONENT = "aaiutil";
	
	private static Client client = null;
	
	private String restSrvrBaseURL;
	
	//To do - Come up with helper function that will automatically
	//generate the REST API path based on path parameter(s) and query parameter(s)!
	public static final String REST_APIPATH_COMPLEXES = "cloud-infrastructure/complexes";
	public static final String REST_APIPATH_COMPLEX = "cloud-infrastructure/complexes/complex/";
	public static final String REST_APIPATH_PSERVERS = "cloud-infrastructure/pservers";
	public static final String REST_APIPATH_PSERVER = "cloud-infrastructure/pservers/pserver/";
	public static final String REST_APIPATH_PHYSICALLINKS = "network/physical-links/";
	public static final String REST_APIPATH_PHYSICALLINK = "network/physical-links/physical-link/";
	public static final String REST_APIPATH_PINTERFACES = "network/p-interfaces/";
	public static final String REST_APIPATH_PINTERFACE = "network/p-interfaces/p-interface/";
	public static final String REST_APIPATH_VPLSPES = "network/vpls-pes/";
	public static final String REST_APIPATH_VPLSPE = "network/vpls-pes/vpls-pe/";
	public static final String REST_APIPATH_UPDATE = "actions/update/";
	public static final String REST_APIPATH_SEARCH = "search/nodes-query?search-node-type=";
	
	public static final String REST_APIPATH_CLOUDREGION = "cloud-infrastructure/cloud-regions/cloud-region/";
	public static final  String REST_APIPATH_TENANT = "cloud-infrastructure/tenants/tenant/";
	public static final  String REST_APIPATH_VPE = "network/vpes/vpe/";
	public static final String REST_APIPATH_VIRTUAL_DATA_CENTER = "cloud-infrastructure/virtual-data-centers/virtual-data-center/";
	public static final String REST_APIPATH_VIRTUAL_DATA_CENTERS = "cloud-infrastructure/virtual-data-centers/";
	//network/generic-vnfs/generic-vnf/{vnf-id}
	public static final String REST_APIPATH_GENERIC_VNF = "network/generic-vnfs/generic-vnf/";
	public static final String REST_APIPATH_GENERIC_VNFS = "network/generic-vnfs";
	public static final String REST_APIPATH_L3_NETWORK = "network/l3-networks/l3-network/";
	public static final String REST_APIPATH_L3_NETWORKS = "network/l3-networks";

	public static final  String REST_APIPATH_VCE = "network/vces/vce/";
	
	public static final  String REST_APIPATH_SERVICE = "service-design-and-creation/services/service/";
	
	/**
	 * Inits the rest client.
	 *
	 * @throws AAIException the AAI exception
	 */
	private static void initRestClient() throws AAIException
	{
		if (client == null) {
			try {
				String useBasicAuth = AAIConfig.get("aai.tools.enableBasicAuth");
				//Client client = null;

				if (useBasicAuth != null && useBasicAuth.equals("true")) {
					
					client = HttpsAuthClient.getBasicAuthClient();
				} else {
					client = HttpsAuthClient.getTwoWaySSLClient();
				}
			}
			catch (KeyManagementException e){
				throw new AAIException("AAI_7117", "KeyManagementException in REST call to DB: " + e.toString());
			} catch (Exception e) {
				throw new AAIException("AAI_7117", " Exception in REST call to DB: " + e.toString());
			}
		}
	}
	
	/**
	 * Sets the rest srvr base URL.
	 *
	 * @param baseURL the base URL
	 * @throws AAIException the AAI exception
	 */
	public void SetRestSrvrBaseURL(String baseURL) throws AAIException
	{
		if (baseURL == null)
			throw new AAIException("AAI_7117", "REST Server base URL cannot be null.");
		restSrvrBaseURL = baseURL;
	}
	
	/**
	 * Gets the rest srvr base URL.
	 *
	 * @return the rest srvr base URL
	 */
	public String getRestSrvrBaseURL() 
	{
		return restSrvrBaseURL;
	}
	
	/**
	 * To do - optimization and automation.  Also make it as generic as possible.
	 *
	 * @param <T> the generic type
	 * @param t the t
	 * @param sourceID the source ID
	 * @param transId the trans id
	 * @param path the path
	 * @param restObject the rest object
	 * @param oldserver the oldserver
	 * @throws AAIException the AAI exception
	 */
	@SuppressWarnings("unchecked")
	public static <T> void Get(T t, String sourceID,  String transId,  String path, RestObject<T> restObject, boolean oldserver) throws AAIException {
		String methodName = "Get";
		String url="";
		transId += ":" + UUID.randomUUID().toString();
		aaiLogger.debug(logline, methodName + " start");
	
		restObject.set(t);
		
		url = AAIConfig.get(AAIConstants.AAI_SERVER_URL) + path;
		initRestClient();
		aaiLogger.debug(logline, url + " for the get REST API");
		
		ClientResponse cres;
		
		if(AAIConfig.get("aai.tools.enableBasicAuth").equals("true"))
		{		cres = client.resource(url)
	         .accept("application/json")
	         .header("X-TransactionId", transId)
	         .header("X-FromAppId",  sourceID)
	         .header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue())
	         .type("application/json")
	         .get(ClientResponse.class);
		}
		else{
			cres = client.resource(url)
			         .accept("application/json")
			         .header("X-TransactionId", transId)
			         .header("X-FromAppId",  sourceID)
			         .header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue())
			         .type("application/json")
			         .get(ClientResponse.class);
		}
//			System.out.println("cres.EntityInputSream()="+cres.getEntityInputStream().toString());
//			System.out.println("cres.tostring()="+cres.toString());
//		System.out.println("CLIENT RESPONSE: "+ cres.getEntity(c));
		
		 if (cres.getStatus() == 200) {
//		     System.out.println(methodName + ": url=" + url);
			 t = (T) cres.getEntity(t.getClass());
			 System.out.println("CLASS: " + t.getClass());
			 restObject.set(t);
			 aaiLogger.debug(logline, methodName + "REST api GET was successfull!");
		    
		 } else {
//		     System.out.println(methodName + ": url=" + url + " failed with status=" + cres.getStatus());
		     throw new AAIException("AAI_7116", methodName +" with status="+cres.getStatus()+", url="+url);
		 }

	}
	
	/**
	 * Gets the.
	 *
	 * @param <T> the generic type
	 * @param t the t
	 * @param requestObj the request obj
	 * @throws AAIException the AAI exception
	 */
	public static <T> void Get(T t, Request<T> requestObj) throws AAIException {
		String methodName = "Get";
		String url="";
		String transId = requestObj.transactionId;
		transId += ":" + UUID.randomUUID().toString();
		aaiLogger.debug(logline, methodName + " start");
	
		requestObj.restObj.set(t);
		
		if (requestObj.oldServer)
			url = AAIConfig.get(AAIConstants.AAI_SERVER_URL_BASE) + "server/" + requestObj.path;
		else
			url = AAIConfig.get(AAIConstants.AAI_SERVER_URL_BASE) + requestObj.path;
		initRestClient();
		
	    try {
		    URL urlObj= new URL(url);
			URI uri = new URI(urlObj.getProtocol(), urlObj.getUserInfo(), urlObj.getHost(), urlObj.getPort(), urlObj.getPath(), urlObj.getQuery(), urlObj.getRef());
			url = uri.toASCIIString();
	    } catch (URISyntaxException | MalformedURLException e) {
			 throw new AAIException("AAI_7116", "bad URL");

		}
		aaiLogger.debug(logline, url + " for the get REST API");
		ClientResponse cres;
		if(AAIConfig.get("aai.tools.enableBasicAuth").equals("true"))
		{
			cres = client.resource(url)
	         .accept("application/json")
	         .header("X-TransactionId", transId)
	         .header("X-FromAppId",  requestObj.fromAppId)
	         .header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue())
	         .type("application/json")
	         .get(ClientResponse.class);
		}
		
		else{
			cres = client.resource(url)
			         .accept("application/json")
			         .header("X-TransactionId", transId)
			         .header("X-FromAppId",  requestObj.fromAppId)
			         .type("application/json")
			         .get(ClientResponse.class);
		}
		
		
		
		
//			System.out.println("cres.EntityInputSream()="+cres.getEntityInputStream().toString());
//			System.out.println("cres.tostring()="+cres.toString());
			
		 if (cres.getStatus() == 200) {
//		     System.out.println(methodName + ": url=" + url);
			 t = (T) cres.getEntity(t.getClass());
			 requestObj.restObj.set(t);
			 aaiLogger.debug(logline, methodName + "REST api GET was successfull!");
		    
		 } else {
//		     System.out.println(methodName + ": url=" + url + " failed with status=" + cres.getStatus());
		     throw new AAIException("AAI_7116", methodName +" with status="+cres.getStatus()+", url="+url);
		 }

	}
	
	/**
	 * Put.
	 *
	 * @param <T> the generic type
	 * @param t the t
	 * @param requestObj the request obj
	 * @throws AAIException the AAI exception
	 */
	public static <T> void Put(T t, Request<T> requestObj) throws AAIException {
		String methodName = "Put";
		String url="";
		String transId = requestObj.transactionId;
		transId += ":" + UUID.randomUUID().toString();
		logline.init(COMPONENT, transId, requestObj.fromAppId, methodName);
		aaiLogger.debug(logline, methodName + " start");		

		initRestClient();
		
		if (requestObj.oldServer)
			url = AAIConfig.get(AAIConstants.AAI_SERVER_URL_BASE) + "server/" + requestObj.path;
		else
			url = AAIConfig.get(AAIConstants.AAI_SERVER_URL_BASE) + requestObj.path;
		
		
		logline.add("path", url);

	    try {
		    URL urlObj= new URL(url);
			URI uri = new URI(urlObj.getProtocol(), urlObj.getUserInfo(), urlObj.getHost(), urlObj.getPort(), urlObj.getPath(), urlObj.getQuery(), urlObj.getRef());
			url = uri.toASCIIString();
	    } catch (URISyntaxException | MalformedURLException e) {
			 throw new AAIException("AAI_7116", "bad URL");

		}
	    ClientResponse cres;
	    if(AAIConfig.get("aai.tools.enableBasicAuth").equals("true")){
		 cres = client.resource(url)
	         .accept("application/json")
	         .header("X-TransactionId", transId)
	         .header("X-FromAppId",  requestObj.fromAppId)
	         .header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue())
	         .type("application/json")
	         .entity(t)
	         .put(ClientResponse.class);
	    }
	    else{
	    	 cres = client.resource(url)
	   	         .accept("application/json")
	   	         .header("X-TransactionId", transId)
	   	         .header("X-FromAppId",  requestObj.fromAppId)
	   	         .type("application/json")
	   	         .entity(t)
	   	         .put(ClientResponse.class);
	    }
	
//			System.out.println("cres.tostring()="+cres.toString());
		
		int statuscode = cres.getStatus();
		if ( statuscode >= 200 && statuscode <= 299 ) {
//			 aaiLogger.debug(logline, methodName+": url=" + url + ", request=" + path);
			 aaiLogger.info(logline, true, "0");
		 } else {
//			 System.out.println(methodName + ": with url="+url+ " failed with status="+ cres.getStatus() + ":" + cres.getEntity(String.class));
			 aaiLogger.info(logline, false, "AAI_7116");
			 throw new AAIException("AAI_7116", methodName +" with status="+statuscode+", url="+url);
		 }			 
	}
	
	/**
	 *  Multiple Generic Get.
	 *
	 * @param <T> the generic type
	 * @param t the t
	 * @param sourceID the source ID
	 * @param transId the trans id
	 * @param path the path
	 * @param oldserver the oldserver
	 * @return the list
	 * @throws AAIException the AAI exception
	 */
	public static <T> List<T> Get(T t, String sourceID,  String transId,  String path, boolean oldserver) throws AAIException {
		String methodName = "Get";
		String url="";
		transId += ":" + UUID.randomUUID().toString();
		aaiLogger.debug(logline, methodName + " start");
	
		List<T> list;
		
		try {
			url = AAIConfig.get(AAIConstants.AAI_SERVER_URL) + path;
			initRestClient();
			aaiLogger.debug(logline, url + " for the get REST API");
			ClientResponse cres = client.resource(url)
		         .accept("application/json")
		         .header("X-TransactionId", transId)
		         .header("X-FromAppId",  sourceID)
		         .type("application/json")
		         .get(ClientResponse.class);

			
			 if (cres.getStatus() == 200) {
			     String datainJson = cres.getEntity(String.class);
			     list = mapJsonToObjectList(t, datainJson, t.getClass());
	
				 aaiLogger.debug(logline, methodName + "REST api GET was successfull!");
			     return list;
				 
			 } else {
			     System.out.println(methodName + ": url=" + url + " failed with status=" + cres.getStatus());
			     throw new AAIException("AAI_7116", methodName +" with status="+cres.getStatus()+", url="+url);
			 }
		} catch (AAIException e) {
			throw new AAIException("AAI_7116", methodName + " with url="+url+ ", Exception: " + e.toString());
		} catch (Exception e)
		{
			throw new AAIException("AAI_7116", methodName + " with url="+url+ ", Exception: " + e.toString());
		
		}

	}

   /**
    * Map json to object list.
    *
    * @param <T> the generic type
    * @param typeDef the type def
    * @param json the json
    * @param clazz the clazz
    * @return the list
    * @throws Exception the exception
    */
   private static <T> List<T> mapJsonToObjectList(T typeDef,String json, Class clazz) throws Exception
   {
      List<T> list;
      ObjectMapper mapper = new ObjectMapper();
      System.out.println(json);
      TypeFactory t = TypeFactory.defaultInstance();
      list = mapper.readValue(json, t.constructCollectionType(ArrayList.class,clazz));

      return list;
   }
	   
	/**
	 * Put.
	 *
	 * @param <T> the generic type
	 * @param t the t
	 * @param sourceID the source ID
	 * @param transId the trans id
	 * @param path the path
	 * @throws AAIException the AAI exception
	 */
	public static <T> void Put(T t, String sourceID,  String transId,  String path) throws AAIException {
		Put( t, sourceID, transId, path, false);
	}

	/**
	 * Put.
	 *
	 * @param <T> the generic type
	 * @param t the t
	 * @param sourceID the source ID
	 * @param transId the trans id
	 * @param path the path
	 * @param oldserver the oldserver
	 * @throws AAIException the AAI exception
	 */
	public static <T> void Put(T t, String sourceID,  String transId,  String path, boolean oldserver) throws AAIException {
		String methodName = "Put";
		String url="";
		transId += ":" + UUID.randomUUID().toString();
		logline.init(COMPONENT, transId, sourceID, methodName);
		logline.add("path", path);
		aaiLogger.debug(logline, methodName + " start");		

		initRestClient();
		
		url = AAIConfig.get(AAIConstants.AAI_SERVER_URL) + path;
		ClientResponse cres;
		if(AAIConfig.get("aai.tools.enableBasicAuth").equals("true")){
		 cres = client.resource(url)
	         .accept("application/json")
	         .header("X-TransactionId", transId)
	         .header("X-FromAppId",  sourceID)
	         .header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue())
	         .type("application/json")
	         .entity(t)
	         .put(ClientResponse.class);
		}
		
		else{
			cres = client.resource(url)
			         .accept("application/json")
			         .header("X-TransactionId", transId)
			         .header("X-FromAppId",  sourceID)
			         .type("application/json")
			         .entity(t)
			         .put(ClientResponse.class);
			
		}
		int statuscode = cres.getStatus();
		if ( statuscode >= 200 && statuscode <= 299 ) {
			 aaiLogger.info(logline, true, "0");
		 } else {
			 aaiLogger.info(logline, false, "AAI_7116");
			 throw new AAIException("AAI_7116", methodName +" with status="+statuscode+", url="+url + ", msg=" + cres.getEntity(String.class));
		 }			 
	}
	
	/**
	 * Delete.
	 *
	 * @param requestObj the request obj
	 * @throws AAIException the AAI exception
	 */
	public static void Delete(Request requestObj) throws AAIException {
		String methodName = "Delete";

		String url="";
		String transId = requestObj.transactionId;
		transId += ":" + UUID.randomUUID().toString();
		logline.init(COMPONENT, transId, requestObj.fromAppId, methodName);
		aaiLogger.debug(logline, methodName + " start");		

		initRestClient();
		
		if (requestObj.oldServer)
			url = AAIConfig.get(AAIConstants.AAI_SERVER_URL_BASE) + "servers/" + requestObj.path;
		else
			url = AAIConfig.get(AAIConstants.AAI_SERVER_URL_BASE) + requestObj.path;
		
		
		logline.add("path", url);

	    try {
		    URL urlObj= new URL(url);
			URI uri = new URI(urlObj.getProtocol(), urlObj.getUserInfo(), urlObj.getHost(), urlObj.getPort(), urlObj.getPath(), urlObj.getQuery(), urlObj.getRef());
			url = uri.toASCIIString();
	    } catch (URISyntaxException | MalformedURLException e) {
			 throw new AAIException("AAI_7116", "bad URL");

		}
	    ClientResponse cres;
	    if(AAIConfig.get("aai.tools.enableBasicAuth").equals("true")){
		cres = client.resource(url)
		         .accept("application/json")
		         .header("X-TransactionId", transId)
		         .header("X-FromAppId",  requestObj.fromAppId)
		         .header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue())
		         .type("application/json")
		         .entity("{}")
		         .delete(ClientResponse.class);
	    }
	    else{
	    	cres = client.resource(url)
			         .accept("application/json")
			         .header("X-TransactionId", transId)
			         .header("X-FromAppId",  requestObj.fromAppId)
			         .type("application/json")
			         .entity("{}")
			         .delete(ClientResponse.class);
	    }
//		System.out.println("cres.tostring()="+cres.toString());
		
		 if (cres.getStatus() == 204) {
//			 aaiLogger.debug(logline, methodName+": url=" + url);
			 aaiLogger.info(logline, true, "0");
		 } else {
//			 System.out.println(methodName + ": with url="+url+ " failed with status="+ cres.getStatus() + ":" + cres.getEntity(String.class));
			 aaiLogger.info(logline, false, "AAI_7116");
			 throw new AAIException("AAI_7116", methodName +" with status="+cres.getStatus()+", url="+url);
		 } 

	}
	
	/**
	 * Delete.
	 *
	 * @param sourceID the source ID
	 * @param transId the trans id
	 * @param path the path
	 * @throws AAIException the AAI exception
	 */
	public static void Delete(String sourceID,  String transId,  String path) throws AAIException {
		String methodName = "Delete";
		String url="";
		transId += ":" + UUID.randomUUID().toString();
		logline.init(COMPONENT, transId, sourceID, methodName);
		aaiLogger.debug(logline, methodName + " start");
		logline.add("path", path);
		
		initRestClient();
		String request = "{}";
		url = AAIConfig.get(AAIConstants.AAI_SERVER_URL) + path;			
	
		ClientResponse cres;
		if(AAIConfig.get("aai.tools.enableBasicAuth").equals("true")){
		cres = client.resource(url)
		         .accept("application/json")
		         .header("X-TransactionId", transId)
		         .header("X-FromAppId",  sourceID)
		         .header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue())
		         .type("application/json")
		         .entity(request)
		         .delete(ClientResponse.class);
		}
		else{
			cres = client.resource(url)
			         .accept("application/json")
			         .header("X-TransactionId", transId)
			         .header("X-FromAppId",  sourceID)
			         .type("application/json")
			         .entity(request)
			         .delete(ClientResponse.class);
		}
//		System.out.println("cres.tostring()="+cres.toString());
		
		if (cres.getStatus() == 404) { // resource not found
			String msg = "Resource does not exist...: " + cres.getStatus()
					+ ":" + cres.getEntity(String.class);
			//System.out.println("\n" + msg);
			logline.add("msg", msg );
			aaiLogger.info(logline, false, "AAI_7404");
            throw new AAIException("AAI_7404", "Resource does not exist");
		} else if (cres.getStatus() == 200  || cres.getStatus() == 204){
			//System.out.println("\nResource " + url + " deleted");
			logline.add("msg", "Resource " + url + " deleted");
			aaiLogger.info(logline, true, "0");
		} else {
			String msg = "Deleting Resource failed: " + cres.getStatus()
				+ ":" + cres.getEntity(String.class);
			//System.out.println("\n" + msg);
			logline.add("msg", msg);
			aaiLogger.info(logline, false, "AAI_7116");
            throw new AAIException("AAI_7116", "Error during DELETE");
		}
		 /*if (cres.getStatus() == 204) {
//			 aaiLogger.debug(logline, methodName+": url=" + url);
			 aaiLogger.info(logline, true, "0");
		 } else {
//			 System.out.println(methodName + ": with url="+url+ " failed with status="+ cres.getStatus() + ":" + cres.getEntity(String.class));
			 aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			 throw new AAIException("AAI_7116", methodName +" with status="+cres.getStatus()+", url="+url);
		 } */
	}
	
    /**
     * Post.
     *
     * @param <T> the generic type
     * @param t the t
     * @param sourceID the source ID
     * @param transId the trans id
     * @param path the path
     * @return the string
     * @throws Exception the exception
     */
    public static <T> String Post(T t, String sourceID,  String transId,  String path) throws Exception {
        String methodName = "Post";
        String url="";
        transId += ":" + UUID.randomUUID().toString();
        logline.init(COMPONENT, transId, sourceID, methodName);
        logline.add("path", path);
        aaiLogger.debug(logline, methodName + " start");        
        
        try {
            
            initRestClient();    
    
            url = AAIConfig.get(AAIConstants.AAI_SERVER_URL) + path;
            
            ClientResponse cres = client.resource(url)
                 .accept("application/json")
                 .header("X-TransactionId", transId)
                 .header("X-FromAppId",  sourceID)
                 .type("application/json")
                 .entity(t)
                 .post(ClientResponse.class);
            
            int statuscode = cres.getStatus();
    		if ( statuscode >= 200 && statuscode <= 299 ) {
    //             aaiLogger.debug(logline, methodName+": url=" + url + ", request=" + path);
    
                 aaiLogger.debug(logline, methodName + "REST api POST was successful!");
                 aaiLogger.info(logline, true, "0");
                 return cres.getEntity(String.class);
             } else {
    //             System.out.println(methodName + ": with url="+url+ " failed with status="+ cres.getStatus() + ":" + cres.getEntity(String.class));
                 aaiLogger.info(logline, false, "AAI_7116");
                 throw new AAIException("AAI_7116", methodName +" with status="+statuscode+", url="+url + ", msg=" + cres.getEntity(String.class));
             }    
        
        } catch (AAIException e) {
            throw new AAIException("AAI_7116", methodName + " with url="+url+ ", Exception: " + e.toString());
        } catch (Exception e)
        {
            throw new AAIException("AAI_7116", methodName + " with url="+url+ ", Exception: " + e.toString());
        
        }
    }

	
    /**
     * Gets the single instance of RestController.
     *
     * @param <T> the generic type
     * @param clazz the clazz
     * @return single instance of RestController
     * @throws IllegalAccessException the illegal access exception
     * @throws InstantiationException the instantiation exception
     */
    public static <T> T getInstance(Class<T> clazz) throws IllegalAccessException, InstantiationException
	{
		return clazz.newInstance();
	} 
	
    /**
     * Does resource exist.
     *
     * @param <T> the generic type
     * @param resourcePath the resource path
     * @param resourceClassName the resource class name
     * @param fromAppId the from app id
     * @param transId the trans id
     * @return the t
     */
    /*
     *     DoesResourceExist
     *     
     *     To check whether a resource exist or get a copy of the existing version of the resource
     *	
     *       Resourcepath: should contain the qualified resource path (including encoded unique key identifier value),
     *       resourceClassName: is the canonical name of the resource class name, 
     *       fromAppId:
     *       transId:
     *       
     *     Will return null (if the resource doesn't exist)  (or) 
     *     Will return the specified resource from the Graph.
     *     
     *     Example:
     *     LogicalLink llink = new LogicalLink();
     *     String resourceClassName = llink.getClass().getCanonicalName();
     *     llink = RestController.DoesResourceExist("network/logical-links/logical-link/" + <encoded-link-name>, resourceClassName, fromAppId, transId);
   */
	public static <T> T DoesResourceExist(String resourcePath, String resourceClassName, String fromAppId, String transId) {
		String methodName = "DoesResourceExist";		
		System.out.println(methodName);
		
		logline.init(COMPONENT, transId, fromAppId, "DoesResourceExist");
		
		try {
			
			RestObject<T> restObj = new RestObject<T>();
			@SuppressWarnings("unchecked")
			T resourceObj = (T)getInstance(Class.forName(resourceClassName));
			restObj.set(resourceObj);
			RestController.<T>Get(resourceObj, fromAppId, transId, resourcePath, restObj, false);
			
			resourceObj = restObj.get();
			if (resourceObj != null)
			  return resourceObj;

		} catch (AAIException e) {
			
		} catch (ClientHandlerException che) {
			
		}catch (Exception e) {
			
		}
		
		return null;
	}
	
}
