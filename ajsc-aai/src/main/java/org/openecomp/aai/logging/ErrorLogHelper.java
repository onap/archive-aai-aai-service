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

package org.openecomp.aai.logging;

import java.io.FileInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.apache.commons.lang.StringUtils;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.util.AAIConstants;
import org.openecomp.aai.util.MapperUtil;

/**
 * 
 * This classes loads the application error properties file
 * and provides a method that returns an ErrorObject 
 * 
 */

public class ErrorLogHelper {

	private static AAILogger aaiLogger = new AAILogger(ErrorLogHelper.class.getName());
	private static Properties props = new Properties();
	private static boolean isLoaded = false;
	public static String errorPropertiesPath = null;

	/**
	 * Sets the error props path.
	 *
	 * @param errorPropsPath the new error props path
	 */
	public static void setErrorPropsPath(String errorPropsPath) {
		if(errorPropertiesPath == null || errorPropertiesPath.length() == 0){
			errorPropertiesPath = errorPropsPath;
		}
	}

	/**
	 * Load properties.
	 *
	 * @throws Exception the exception
	 */
	public static void loadProperties() throws Exception{
		LogLine logline = new LogLine();
		logline.init("aaigen", UUID.randomUUID().toString(), "AAI-INIT", "loading error properties");
		if (!isLoaded) {	

			if(errorPropertiesPath == null ) {
				errorPropertiesPath = AAIConstants.AAI_HOME_ETC_APP_PROPERTIES + "error.properties";  // set default if not configured
			}
			logline.add("file", errorPropertiesPath);
			String filepath =  errorPropertiesPath; 

			if(filepath.startsWith("error")) {
				props.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(filepath)); 
			} else {
				FileInputStream fis = new FileInputStream(filepath);
				props.load(fis);
				fis.close();
			}
			isLoaded = true;
			aaiLogger.info(logline, true, "0");

		}
	}

	/**
	 * Gets the error object.
	 *
	 * @param key the key
	 * @return the error object
	 */
	/* method that returns an error object for a key (error code) based 
	 * on the error.properties file
	 * @param key - of the form "AAI_nnnn" that has an entry in the error.properties file
	 * @return ErrorObject 
	 */
	public static ErrorObject getErrorObject(String key){
		return getErrorObject(key, null);
	}

	/**
	 * Gets the error object.
	 *
	 * @param key the key
	 * @param details the details
	 * @return the error object
	 */
	/* method that returns an error object for a key (error code) based 
	 * on the error.properties file
	 * @param key - of the form "AAI_nnnn" that has an entry in the error.properties file
	 * @param details - set the details of the errorObject to this
	 * @return ErrorObject 
	 */
	public static ErrorObject getErrorObject(String key, String details){
		ErrorObject errorObject = new ErrorObject();
		try {
			if (!isLoaded) {
				// The properties were not successfully loaded, try again
				loadProperties();
			}
			if ((null != key) && (0 != key.trim().length())) {

				String value = "";
				value = (String) props.get(key);
				if ((null == value) || (0 == value.trim().length())) {
					throw new Exception("getErrorObject():Value is null for key: " + key);
				} else {

					String errorProperties[] = value.split(":");

					errorObject.setDisposition(errorProperties[0].trim());
					errorObject.setCategory(errorProperties[1].trim());
					errorObject.setSeverity(errorProperties[2].trim());
					errorObject.setErrorCode(errorProperties[3].trim());
					errorObject.setHTTPResponseCode(errorProperties[4].trim());
					if (errorProperties.length != 7) {
						throw new Exception("error.properties line for "+key+" is improperly formatted");
					} else {
						errorObject.setRESTErrorCode(errorProperties[5].trim());
						errorObject.setErrorText(errorProperties[6].trim());
					}
					errorObject.setDetails(details);
				}
			} else {
				throw new Exception("getErrorObject():Key is null");
			}

		} catch (Exception e) {
			errorObject.setDisposition("5");
			errorObject.setCategory("4");
			errorObject.setSeverity("FATAL");
			errorObject.setErrorCode("4004");
			errorObject.setHTTPResponseCode(Status.INTERNAL_SERVER_ERROR); // 500
			errorObject.setRESTErrorCode("3002");
			if ( e.getCause() != null && e.getCause().getMessage() != null)
				errorObject.setErrorText("Error reading/parsing the error properties file trying to get error object for " + key + ":" + e.getMessage() + ":" + e.getCause().getMessage());
			else
				errorObject.setErrorText("Error reading/parsing the error properties file trying to get error object for " + key + ":" + e.getMessage());		
		}
		return errorObject;
	}

	/**
	 * Determines whether category is policy or not.  If policy (1), this is a POL error, else it's a SVC error.
	 * The AAIRESTException may contain a different ErrorObject than that created with the REST error key.
	 * This allows lower level exception detail to be returned to the client to help troubleshoot the problem.
	 * If no error object is embedded in the AAIException, one will be created using the error object from the AAIException.
	 * @param are must have a restError value whose numeric value must match what should be returned in the REST API
	 * @param variables optional list of variables to flesh out text in error string
	 * @param logline LogLine
	 * @return appropriately formatted JSON response per the REST API spec.
	 * @deprecated
	 */
	public static String getRESTAPIErrorResponse(AAIException are, ArrayList<String> variables, LogLine logline) {
		List<MediaType> acceptHeaders = new ArrayList<MediaType>();
		acceptHeaders.add(MediaType.APPLICATION_JSON_TYPE);

		return getRESTAPIErrorResponse(acceptHeaders, are, variables, logline);
	}

	/**
	 * Determines whether category is policy or not.  If policy (1), this is a POL error, else it's a SVC error.
	 * The AAIRESTException may contain a different ErrorObject than that created with the REST error key.
	 * This allows lower level exception detail to be returned to the client to help troubleshoot the problem.
	 * If no error object is embedded in the AAIException, one will be created using the error object from the AAIException.
	 *
	 * @param acceptHeadersOrig the accept headers orig
	 * @param are must have a restError value whose numeric value must match what should be returned in the REST API
	 * @param variables optional list of variables to flesh out text in error string
	 * @param logline LogLine
	 * @return appropriately formatted JSON response per the REST API spec.
	 */
	public static String getRESTAPIErrorResponse(List<MediaType> acceptHeadersOrig, AAIException are, ArrayList<String> variables, LogLine logline) {;


		StringBuilder text = new StringBuilder();
		String response = null;
		
		List<MediaType> acceptHeaders = new ArrayList<MediaType>();
		// we might have an exception but no accept header, so we'll set default to JSON
		boolean foundValidAcceptHeader = false;
		for (MediaType mt : acceptHeadersOrig) {
			if (MediaType.APPLICATION_XML_TYPE.isCompatible(mt) ||
					MediaType.APPLICATION_JSON_TYPE.isCompatible(mt)) {
				acceptHeaders.add(mt);
				foundValidAcceptHeader = true;
			}
		}
		if (foundValidAcceptHeader == false) { 
			// override the exception, client needs to set an appropriate Accept header
			are = new AAIException("AAI_4014");
			acceptHeaders.add(MediaType.APPLICATION_JSON_TYPE);
		}

		ErrorObject eo = are.getErrorObject();
		
		int restErrorCode = Integer.parseInt(eo.getRESTErrorCode());
		ErrorObject restErrorObject = ErrorLogHelper.getErrorObject("AAI_"+restErrorCode);		
		if (restErrorObject == null) {
			restErrorObject = eo;
		}
		text.append(restErrorObject.getErrorText());
		
		
		
		// We want to always append the (msg=%n) (ec=%n+1) to the text, but have to find value of n
		// This assumes that the variables in the ArrayList, which might be more than are needed to flesh out the
		// error, are ordered based on the error string.
		int localDataIndex = StringUtils.countMatches(restErrorObject.getErrorText(), "%");
		text.append(" (msg=%").append(localDataIndex+1).append(") (ec=%").append(localDataIndex+2).append(")");

		if (variables == null) 
		{
			variables = new ArrayList<String>();
		}
	
		if (variables.size() < localDataIndex) {
			aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_4011", "data missing for rest error"), logline, null);
			while (variables.size() < localDataIndex) {
				variables.add("null");
			}
		}
		
		// This will put the error code and error text into the right positions
		if (eo.getDetails() == null || eo.getDetails().length() == 0) {
			variables.add(localDataIndex++, eo.getErrorText());
		}
		else {
			variables.add(localDataIndex++, eo.getErrorText() + ":" + eo.getDetails());
		}
		variables.add(localDataIndex, eo.getErrorCodeString());
		
		for (MediaType mediaType : acceptHeaders) {
		if (MediaType.APPLICATION_XML_TYPE.isCompatible(mediaType)) {
				JAXBContext context = null;
				try {
					if(eo.getCategory().equals("1")) {
						
						context = JAXBContext.newInstance(org.openecomp.aai.domain.restPolicyException.Fault.class);
						Marshaller m = context.createMarshaller();
						m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
						m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
						
						org.openecomp.aai.domain.restPolicyException.ObjectFactory factory = new org.openecomp.aai.domain.restPolicyException.ObjectFactory();
						org.openecomp.aai.domain.restPolicyException.Fault fault = factory.createFault();
						org.openecomp.aai.domain.restPolicyException.Fault.RequestError requestError = factory.createFaultRequestError();
						org.openecomp.aai.domain.restPolicyException.Fault.RequestError.PolicyException policyException = factory.createFaultRequestErrorPolicyException();
						org.openecomp.aai.domain.restPolicyException.Fault.RequestError.PolicyException.Variables polvariables = factory.createFaultRequestErrorPolicyExceptionVariables();
						
						policyException.setMessageId("POL" + eo.getRESTErrorCode());
						policyException.setText(text.toString());
						for (int i=0;i<variables.size();i++)
						{
							polvariables.getVariable().add(variables.get(i));
						}
						policyException.setVariables(polvariables);
						requestError.setPolicyException(policyException);
						fault.setRequestError(requestError);
						
						StringWriter sw = new StringWriter();
						m.marshal(fault, sw);

						response = sw.toString();

					} else {
						
						context = JAXBContext.newInstance(org.openecomp.aai.domain.restServiceException.Fault.class);
						Marshaller m = context.createMarshaller();
						m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
						m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
						
						org.openecomp.aai.domain.restServiceException.ObjectFactory factory = new org.openecomp.aai.domain.restServiceException.ObjectFactory();
						org.openecomp.aai.domain.restServiceException.Fault fault = factory.createFault();
						org.openecomp.aai.domain.restServiceException.Fault.RequestError requestError = factory.createFaultRequestError();
						org.openecomp.aai.domain.restServiceException.Fault.RequestError.ServiceException serviceException = factory.createFaultRequestErrorServiceException();
						org.openecomp.aai.domain.restServiceException.Fault.RequestError.ServiceException.Variables svcvariables = factory.createFaultRequestErrorServiceExceptionVariables();
						serviceException.setMessageId("SVC" + eo.getRESTErrorCode());
						serviceException.setText(text.toString());
						for (int i=0;i<variables.size();i++)
						{
							svcvariables.getVariable().add(variables.get(i));
						}
						serviceException.setVariables(svcvariables);
						requestError.setServiceException(serviceException);
						fault.setRequestError(requestError);

						StringWriter sw = new StringWriter();
						m.marshal(fault, sw);

						response = sw.toString();

					}
				} catch (Exception ex) {
					restErrorObject.setDetails("We were unable to create a rest exception to return on an API because of a parsing error");
					aaiLogger.error(restErrorObject, logline, ex);
				}
			}
		else  {		
			try {
				if(eo.getCategory().equals("1")) {
					org.openecomp.aai.domain.restPolicyException.RESTResponse restresp = new org.openecomp.aai.domain.restPolicyException.RESTResponse();
					org.openecomp.aai.domain.restPolicyException.RequestError reqerr = new org.openecomp.aai.domain.restPolicyException.RequestError();
					org.openecomp.aai.domain.restPolicyException.PolicyException polexc = new org.openecomp.aai.domain.restPolicyException.PolicyException();
					polexc.setMessageId("POL" + eo.getRESTErrorCode());
					polexc.setText(text.toString());
					polexc.setVariables(variables);
					reqerr.setPolicyException(polexc);
					restresp.setRequestError(reqerr);
					response = (MapperUtil.writeAsJSONString((Object) restresp));
	
				} else {
					org.openecomp.aai.domain.restServiceException.RESTResponse restresp = new org.openecomp.aai.domain.restServiceException.RESTResponse();
					org.openecomp.aai.domain.restServiceException.RequestError reqerr = new org.openecomp.aai.domain.restServiceException.RequestError();
					org.openecomp.aai.domain.restServiceException.ServiceException svcexc = new org.openecomp.aai.domain.restServiceException.ServiceException();
					svcexc.setMessageId("SVC" + eo.getRESTErrorCode());
					svcexc.setText(text.toString());
					svcexc.setVariables(variables);
					reqerr.setServiceException(svcexc);
					restresp.setRequestError(reqerr);
					response = (MapperUtil.writeAsJSONString((Object) restresp));
				}
			} catch (AAIException ex) {
				restErrorObject.setDetails("We were unable to create a rest exception to return on an API because of a parsing error");
				aaiLogger.error(restErrorObject, logline, ex);
			}
		}
		}


		return response;
	}

	/**
	 * Gets the RESTAPI error response with logging.
	 *
	 * @param acceptHeadersOrig the accept headers orig
	 * @param are the are
	 * @param variables the variables
	 * @param logline the logline
	 * @return the RESTAPI error response with logging
	 */
	public static String getRESTAPIErrorResponseWithLogging(List<MediaType> acceptHeadersOrig, AAIException are, ArrayList<String> variables, LogLine logline) {;
		String response = ErrorLogHelper.getRESTAPIErrorResponse(acceptHeadersOrig, are, variables, logline);
		
		aaiLogger.error(are.getErrorObject(), logline, are);
		aaiLogger.info(logline, false, are.getErrorObject().getErrorCodeString());
		
		return response;
		
	}
	
	/**
	 * Gets the RESTAPI info response.
	 *
	 * @param acceptHeaders the accept headers
	 * @param areList the are list
	 * @param logline the logline
	 * @return the RESTAPI info response
	 */
	public static Object getRESTAPIInfoResponse(List<MediaType> acceptHeaders, HashMap<AAIException,ArrayList<String>> areList, LogLine logline) {
		
		Object respObj = null;

		org.openecomp.aai.domain.restResponseInfo.ObjectFactory factory = new org.openecomp.aai.domain.restResponseInfo.ObjectFactory();
		org.openecomp.aai.domain.restResponseInfo.Info info = factory.createInfo();
		org.openecomp.aai.domain.restResponseInfo.Info.ResponseMessages responseMessages = factory.createInfoResponseMessages();
		Iterator<Map.Entry<AAIException, ArrayList<String>>> it = areList.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<AAIException,ArrayList<String>> pair = (Map.Entry<AAIException, ArrayList<String>>)it.next();
			AAIException are = pair.getKey();
			ArrayList<String> variables = pair.getValue();
			//System.out.println(pair.getKey() + " = " + pair.getValue());

			StringBuilder text = new StringBuilder();

			ErrorObject eo = are.getErrorObject();

			int restErrorCode = Integer.parseInt(eo.getRESTErrorCode());
			ErrorObject restErrorObject = ErrorLogHelper.getErrorObject("AAI_"+String.format("%04d", restErrorCode));		
			if (restErrorObject == null) {
				restErrorObject = eo;
			}
			text.append(restErrorObject.getErrorText());

			// We want to always append the (msg=%n) (ec=%n+1) to the text, but have to find value of n
			// This assumes that the variables in the ArrayList, which might be more than are needed to flesh out the
			// error, are ordered based on the error string.
			int localDataIndex = StringUtils.countMatches(restErrorObject.getErrorText(), "%");
			text.append(" (msg=%").append(localDataIndex+1).append(") (rc=%").append(localDataIndex+2).append(")");

			if (variables == null) 
			{
				variables = new ArrayList<String>();
			}

			if (variables.size() < localDataIndex) {
				aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_4011", "data missing for rest error"), logline, null);
				while (variables.size() < localDataIndex) {
					variables.add("null");
				}
			}

			// This will put the error code and error text into the right positions
			if (eo.getDetails() == null) {
				variables.add(localDataIndex++, eo.getErrorText());
			}
			else {
				variables.add(localDataIndex++, eo.getErrorText() + ":" + eo.getDetails());
			}
			variables.add(localDataIndex, eo.getErrorCodeString());
		
			try { 
				org.openecomp.aai.domain.restResponseInfo.Info.ResponseMessages.ResponseMessage responseMessage = factory.createInfoResponseMessagesResponseMessage();
				org.openecomp.aai.domain.restResponseInfo.Info.ResponseMessages.ResponseMessage.Variables infovariables = factory.createInfoResponseMessagesResponseMessageVariables();

				responseMessage.setMessageId("INF" + eo.getRESTErrorCode());
				responseMessage.setText(text.toString());
				for (int i=0;i<variables.size();i++)
				{
					infovariables.getVariable().add(variables.get(i));
				}

				responseMessage.setVariables(infovariables);
				responseMessages.getResponseMessage().add(responseMessage);

			} catch (Exception ex) {
				restErrorObject.setDetails("We were unable to create a rest exception to return on an API because of a parsing error");
				aaiLogger.error(restErrorObject, logline, ex);
			}
		}
		
		info.setResponseMessages(responseMessages);
		respObj = (Object) info;

		return respObj;
	}


		/**
		 * Determines whether category is policy or not.  If policy (1), this is a POL error, else it's a SVC error.
		 * The AAIRESTException may contain a different ErrorObject than that created with the REST error key.
		 * This allows lower level exception detail to be returned to the client to help troubleshoot the problem.
		 * If no error object is embedded in the AAIException, one will be created using the error object from the AAIException.
		 * @param are must have a restError value whose numeric value must match what should be returned in the REST API
		 * @param variables optional list of variables to flesh out text in error string
		 * @param logline LogLine
		 * @return appropriately formatted JSON response per the REST API spec.
		 */
		public static String getRESTAPIPolicyErrorResponseXML(AAIException are, ArrayList<String> variables, LogLine logline) {

			StringBuilder text = new StringBuilder();
			String response = null;
			JAXBContext context = null;



			ErrorObject eo = are.getErrorObject();

			int restErrorCode = Integer.parseInt(eo.getRESTErrorCode());
			ErrorObject restErrorObject = ErrorLogHelper.getErrorObject("AAI_"+restErrorCode);		
			if (restErrorObject == null) {
				restErrorObject = eo;
			}
			text.append(restErrorObject.getErrorText());

			// We want to always append the (msg=%n) (ec=%n+1) to the text, but have to find value of n
			// This assumes that the variables in the ArrayList, which might be more than are needed to flesh out the
			// error, are ordered based on the error string.
			int localDataIndex = StringUtils.countMatches(restErrorObject.getErrorText(), "%");
			text.append(" (msg=%").append(localDataIndex+1).append(") (ec=%").append(localDataIndex+2).append(")");

			if (variables == null) 
			{
				variables = new ArrayList<String>();
			}

			if (variables.size() < localDataIndex) {
				aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_4011", "data missing for rest error"), logline, null);
				while (variables.size() < localDataIndex) {
					variables.add("null");
				}
			}

			// This will put the error code and error text into the right positions
			if (eo.getDetails() == null) {
				variables.add(localDataIndex++, eo.getErrorText());
			}
			else {
				variables.add(localDataIndex++, eo.getErrorText() + ":" + eo.getDetails());
			}
			variables.add(localDataIndex, eo.getErrorCodeString());

			try {
				if(eo.getCategory().equals("1")) {

					context = JAXBContext.newInstance(org.openecomp.aai.domain.restPolicyException.Fault.class);
					Marshaller m = context.createMarshaller();
					m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
					m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");

					org.openecomp.aai.domain.restPolicyException.ObjectFactory factory = new org.openecomp.aai.domain.restPolicyException.ObjectFactory();
					org.openecomp.aai.domain.restPolicyException.Fault fault = factory.createFault();
					org.openecomp.aai.domain.restPolicyException.Fault.RequestError requestError = factory.createFaultRequestError();
					org.openecomp.aai.domain.restPolicyException.Fault.RequestError.PolicyException policyException = factory.createFaultRequestErrorPolicyException();
					org.openecomp.aai.domain.restPolicyException.Fault.RequestError.PolicyException.Variables polvariables = factory.createFaultRequestErrorPolicyExceptionVariables();

					policyException.setMessageId("POL" + eo.getRESTErrorCode());
					policyException.setText(text.toString());
					for (int i=0;i<variables.size();i++)
					{
						polvariables.getVariable().add(variables.get(i));
					}
					policyException.setVariables(polvariables);
					requestError.setPolicyException(policyException);
					fault.setRequestError(requestError);

					StringWriter sw = new StringWriter();
					m.marshal(fault, sw);

					response = sw.toString();

				} else {

					context = JAXBContext.newInstance(org.openecomp.aai.domain.restServiceException.Fault.class);
					Marshaller m = context.createMarshaller();
					m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
					m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");

					org.openecomp.aai.domain.restServiceException.ObjectFactory factory = new org.openecomp.aai.domain.restServiceException.ObjectFactory();
					org.openecomp.aai.domain.restServiceException.Fault fault = factory.createFault();
					org.openecomp.aai.domain.restServiceException.Fault.RequestError requestError = factory.createFaultRequestError();
					org.openecomp.aai.domain.restServiceException.Fault.RequestError.ServiceException serviceException = factory.createFaultRequestErrorServiceException();
					org.openecomp.aai.domain.restServiceException.Fault.RequestError.ServiceException.Variables svcvariables = factory.createFaultRequestErrorServiceExceptionVariables();
					serviceException.setMessageId("POL" + eo.getRESTErrorCode());
					serviceException.setText(text.toString());
					for (int i=0;i<variables.size();i++)
					{
						svcvariables.getVariable().add(variables.get(i));
					}
					serviceException.setVariables(svcvariables);
					requestError.setServiceException(serviceException);
					fault.setRequestError(requestError);

					StringWriter sw = new StringWriter();
					m.marshal(fault, sw);

					response = sw.toString();

				}
			} catch (Exception ex) {
				restErrorObject.setDetails("We were unable to create a rest exception to return on an API because of a parsing error");
				aaiLogger.error(restErrorObject, logline, ex);
			}
			return response;
		}
	}
