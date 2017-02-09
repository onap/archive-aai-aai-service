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

package org.openecomp.aai.dmaap.aaiWorkload.consumer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.javatuples.Pair;
import org.json.JSONException;
import org.json.JSONObject;
import org.openecomp.aai.dmaap.AAIDmaapPublisher;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;
import org.openecomp.aai.logging.LogLine;

import com.att.nsa.mr.client.MRPublisher.message;
import com.sun.jersey.api.client.ClientResponse;

public class AAIWorkloadEventProcessor {

	private static String fromAppId = "AAIWorkloadConsumerScheduledTask";
	private static String COMPONENT = "DMAAP-AAI-WORKLOAD";
	private AAILogger aaiLogger = new AAILogger(AAIWorkloadEventProcessor.class.getName());
	private String transId = "";
	
	private JSONObject event;
	private JSONObject eventHeader;
	private JSONObject eventBody;

	private JSONObject responseHeader;
	private JSONObject responseBody;
	private String aaiWorkloadPublisherPropertiesFile;
	private String aaiWorkloadStatusPublisherPropertiesFile;
	

	/**
	 * 
	 * @param aaiWorkloadPublisherPropertiesFile
	 * @param aaiWorkloadStatusPublisherPropertiesFile
	 * @param transId 
	 */
	public AAIWorkloadEventProcessor(String aaiWorkloadPublisherPropertiesFile, String aaiWorkloadStatusPublisherPropertiesFile, String transId) {
		this.transId = transId;
		this.aaiWorkloadPublisherPropertiesFile = aaiWorkloadPublisherPropertiesFile;
		this.aaiWorkloadStatusPublisherPropertiesFile = aaiWorkloadStatusPublisherPropertiesFile;
	}

	/**
	 * 
	 * @param eventMessage
	 * @return
	 */
	public Pair<AAIWorkloadEventStatus, String> process(String eventMessage) {

		LogLine logline = new LogLine();
		logline.init(COMPONENT, this.transId, fromAppId, "process(String eventMessage)");
		
		Pair<AAIWorkloadEventStatus, String> status = null;

		this.event = null;
		this.eventHeader = null;
		this.eventBody = null;

		try {
			aaiLogger.debug(logline, "Processing event: " + eventMessage);
			this.event = new JSONObject(eventMessage);
		} catch (JSONException je) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "ERROR: Event is not valid JSON." + je.getMessage());
			aaiLogger.error(errorObject, logline, je);
			return this.statusPair(AAIWorkloadEventStatus.FAILUE, "ERROR: Event is not valid JSON." + je.getMessage());
		}

		try {
			aaiLogger.debug(logline, "Validating event header.");
			this.validateEventHeader(this.event);
			this.generateEventResponseHeader();
		} catch (JSONException je) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "ERROR: Event header is not valid." + je.getMessage());
			aaiLogger.error(errorObject, logline, je);
			return this.statusPair(AAIWorkloadEventStatus.FAILUE, "ERROR: Event header is not valid." + je.getMessage());
		}

		try {
			aaiLogger.debug(logline, "Generating status event header.");
			this.generateEventResponseHeader();
		} catch (JSONException je) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Failed to create response header." + je.getMessage());
			aaiLogger.error(errorObject, logline, je);
			this.responseHeader = null;
		}

		try {
			aaiLogger.debug(logline, "Processing event body.");
			eventBody = this.event.getJSONObject("event-body");
		} catch (JSONException je) {
			try {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "ERROR: Event body not valid JSON." + je.getMessage());
				aaiLogger.error(errorObject, logline, je);
				status = this.statusPair(AAIWorkloadEventStatus.FAILUE, "ERROR: Event body not valid JSON." + je.getMessage());
				this.publishStatusResponse(status);
				return status;
			} catch (JSONException | IOException e) {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "WARNING: Failed to pulish status " + e.getMessage());
				aaiLogger.error(errorObject, logline, je);
				return this.statusPair(AAIWorkloadEventStatus.FAILED_TO_PUBLISH_STATUS, "WARNING: Failed to pulish status " + e.getMessage());
			}
		}

		// initialize aai client, on failure to initialize republish the event.
		AAIClient aaiClient = null;
		try {
			aaiClient = new AAIClient();
		} catch (KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException | AAIException | IOException e) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "ERROR: AAI Client failed to initalize." + e.getMessage());
			aaiLogger.error(errorObject, logline, e);
			return this.republishEvent();
		}

		// put to aai, on socket timeout exception republish the event
		ClientResponse resp = null;
		try {
			aaiLogger.debug(logline, "Calling aai bulk add on event body.");
			resp = aaiClient.put("bulkadd", this.eventBody, this.eventHeader.getString("source-name"));
		} catch (Exception e) {
			if (e instanceof SocketTimeoutException) {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "AAI client timed out during put. Attempting to republish to topic. " + e.getMessage());
				aaiLogger.error(errorObject, logline, e);
				return this.republishEvent();
			} else if (e instanceof SSLHandshakeException) {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "AAI client handshake error during put. Attempting to republish to topic. " + e.getMessage());
				aaiLogger.error(errorObject, logline, e);
				return this.republishEvent();
			} else {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "AAI Unknown error during put. Failure. Attempting to republish to topic. " + e.getMessage());
				aaiLogger.error(errorObject, logline, e);
				status = this.statusPair(AAIWorkloadEventStatus.FAILUE, "AAI Unknown error during put. Failure.");
				try {
					this.publishStatusResponse(status);
				} catch (JSONException | IOException e1) {
					errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Failed to publish status. " + e.getMessage());
					aaiLogger.error(errorObject, logline, e);
				}
				return status;
			}
		}

		int respStatusCode = resp.getStatus();
		String responsePayload = resp.getEntity(String.class);

		if (responsePayload == null) {
			responsePayload = "";
		}
		
		aaiLogger.debug(logline, "AAI response status code. " + respStatusCode);
		aaiLogger.debug(logline, "AAI response status payload. " + responsePayload);

		try {
			JSONObject responsePayloadJO = new JSONObject(responsePayload);
			JSONObject updatedResponsePayloadJO = new JSONObject();
			updatedResponsePayloadJO.put("aai-put-status", respStatusCode);
			updatedResponsePayloadJO.put("aai-put-response", responsePayloadJO);
			responsePayload = updatedResponsePayloadJO.toString();

			if (respStatusCode == 201) {
				aaiLogger.debug(logline, "Successfully bulk add. ");
				this.publishStatusResponse(this.statusPair(AAIWorkloadEventStatus.SUCCESS, responsePayload));
			} else {
				aaiLogger.debug(logline, "Unsuccessfully bulk add. ");
				this.publishStatusResponse(this.statusPair(AAIWorkloadEventStatus.FAILUE, responsePayload));
			}
		} catch (JSONException e) {

		} catch (FileNotFoundException e) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Failed to publish status due to status publisher property file not found. " + e.getMessage());
			aaiLogger.error(errorObject, logline, e);
		} catch (IOException e) {
			ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Connection issue reaching status publisher. " + e.getMessage());
			aaiLogger.error(errorObject, logline, e);
		}

		aaiLogger.debug(logline, "Event processed successfully.");
		return this.statusPair(AAIWorkloadEventStatus.SUCCESS, "Event processed successfully.");

	}

	/**
	 * Publishes the status message to the aaiWorkload status topic.
	 * 
	 * @param statusPair
	 * @throws JSONException
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void publishStatusResponse(Pair<AAIWorkloadEventStatus, String> statusPair) throws JSONException, FileNotFoundException, IOException {
		
		LogLine logline = new LogLine();
		logline.init(COMPONENT, this.transId, fromAppId, "publishStatusResponse(Pair<AAIWorkloadEventStatus, String> statusPair)");
		
		aaiLogger.debug(logline, "Publishing status response.");
		
		if (this.responseHeader == null) {
			aaiLogger.debug(logline, "Status response header failed to be created, no status to publish.");
		} else {
			this.responseBody = new JSONObject();
			this.responseBody.put("status", statusPair.getValue0().toString());
			this.responseBody.put("status-message", statusPair.getValue1());
			JSONObject responsePayload = new JSONObject();
			responsePayload.put("status-event-header", this.responseHeader);
			responsePayload.put("status-event-body", this.responseBody);

			try {
				aaiLogger.debug(logline, "Publishing status message. " + responsePayload);
				publish(this.aaiWorkloadStatusPublisherPropertiesFile, responsePayload);
			} catch (InterruptedException e) {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Caught exception when publishing status message. " + e.getMessage());
				aaiLogger.error(errorObject, logline, e);
			}
		}

	}

	/**
	 * Republish the event to aaiworkload queue. Retry a max of 5 times.
	 * 
	 * @return
	 */
	private Pair<AAIWorkloadEventStatus, String> republishEvent() {
		
		LogLine logline = new LogLine();
		logline.init(COMPONENT, this.transId, fromAppId, "republishEvent()");
		
		int count = 0;
		while (count < 5) {
			count++;
			try {
				aaiLogger.debug(logline, "Republishing message to aai workload topic.");
				publish(this.aaiWorkloadPublisherPropertiesFile, this.event);
				aaiLogger.debug(logline, "Republishing successful.");
				return this.statusPair(AAIWorkloadEventStatus.REPUBLISHED, "Put to aai timed out multiple times, possible issue with server.");
			} catch (Exception e) {
				if (e instanceof IOException || e instanceof InterruptedException) {
					// retries on io exception up to 3 times.
				} else {
					ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000", "Failed to republish due to unknown error. " + e.getMessage());
					aaiLogger.error(errorObject, logline, e);
					return this.statusPair(AAIWorkloadEventStatus.FAILED_TO_REPUBLISH, "Failed to republish due to unknown error. " + e.getMessage());
				}
			}

		}

		return this.statusPair(AAIWorkloadEventStatus.FAILED_TO_REPUBLISH, "Failed to republish event 5 times.");

	}

	private List<message> publish(String publisherPropFile, JSONObject responsePayload) throws FileNotFoundException, IOException, InterruptedException {
		AAIDmaapPublisher awdp = new AAIDmaapPublisher(publisherPropFile);
		return awdp.publishAndClose(responsePayload);
	}

	/**
	 * Using the event header generate the status response header.
	 * 
	 * @throws JSONException
	 */
	private void generateEventResponseHeader() throws JSONException {
		this.responseHeader = new JSONObject(this.eventHeader.toString());
		this.responseHeader.put("id", this.eventHeader.getString("id") + "status");
		this.responseHeader.put("entity-type", "STATUS");
	}

	/**
	 * Validates that the event header has the id and source name for
	 * processing. (needed for status response msg)
	 * 
	 * @param event
	 * @throws JSONException
	 */
	private void validateEventHeader(JSONObject event) throws JSONException {
		eventHeader = event.getJSONObject("event-header");
		if (this.eventHeader.getString("id") == null || this.eventHeader.getString("id").isEmpty()) {
			throw new JSONException("Event header id missing.");
		} else if (this.eventHeader.getString("source-name") == null || this.eventHeader.getString("source-name").isEmpty()) {
			throw new JSONException("Event header source-name missing.");
		}
	}

	private Pair<AAIWorkloadEventStatus, String> statusPair(AAIWorkloadEventStatus status, String msg) {
		if (msg == null) {
			msg = "";
		}
		return new Pair<AAIWorkloadEventStatus, String>(status, msg);
	}

	/**
	 * 
	 * @return
	 */
	public JSONObject getEventHeader() {
		return eventHeader;
	}

	/**
	 * 
	 * @return
	 */
	public JSONObject getEventBody() {
		return eventBody;
	}

}
