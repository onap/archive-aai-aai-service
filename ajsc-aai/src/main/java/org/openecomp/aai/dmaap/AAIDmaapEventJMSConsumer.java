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

package org.openecomp.aai.dmaap;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.json.JSONException;
import org.json.JSONObject;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.rest.HttpMethod;
import org.openecomp.aai.util.AAIConstants;

import com.google.inject.ConfigurationException;

public class AAIDmaapEventJMSConsumer implements MessageListener {

	private final static String COMPONENT = "aaiDmaapEvent";

	private AAILogger aaiLogger = new AAILogger(AAIDmaapEventJMSConsumer.class.getName());

	private AAIDmaapPublisher adp = null;

	public AAIDmaapEventJMSConsumer() throws org.apache.commons.configuration.ConfigurationException {
		super();
		LogLineBuilder llBuilder = new LogLineBuilder("AAIDmaapEventJMSConsumer", "AAIDmaapEventJMSConsumer");
		LogLine logline = llBuilder.build(COMPONENT, "AAIDmaapEventJMSConsumerInit");

		if (this.adp == null) {
			try {
				PropertiesConfiguration config = new PropertiesConfiguration(
						AAIConstants.AAI_HOME_ETC_APP_PROPERTIES + "/aaiEventDMaaPPublisher.properties");
				config.setProperty("DME2preferredRouterFilePath",
						AAIConstants.AAI_HOME_ETC_APP_PROPERTIES + "/preferredRoute.txt");
				config.save();
				this.adp = new AAIDmaapPublisher(
						AAIConstants.AAI_HOME_ETC_APP_PROPERTIES + "/aaiEventDMaaPPublisher.properties");
			} catch (IOException | ConfigurationException e) {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_4000",
						"Error updating dmaap config file for aai event.");
				aaiLogger.error(errorObject, logline, e);
			}
		}

	}

	@Override
	public void onMessage(Message message) {
		
		String jsmMessageTxt = "";
		String aaiEvent = "";
		String transId = "";
		String fromAppId = "";
		
		LogLineBuilder llBuilder = new LogLineBuilder("AAIDmaapEventJMSConsumer", "AAIDmaapEventJMSConsumer");
		LogLine logline = llBuilder.build(COMPONENT, "AAIEventSendToDmaap");

		if (message instanceof TextMessage) {
			try {
				jsmMessageTxt = ((TextMessage) message).getText();
				JSONObject jo = new JSONObject(jsmMessageTxt);
				
				if (jo.getString("aaiEvent") != null && !jo.getString("aaiEvent").isEmpty()) {
					aaiEvent = jo.getString("aaiEvent");
				} else {
					return;
				}
				if (jo.getString("transId") != null) {
					transId = jo.getString("transId");
				}
				if (jo.getString("fromAppId") != null) {
					fromAppId = jo.getString("fromAppId");
				}
				
				llBuilder = new LogLineBuilder(transId, fromAppId);
				logline = llBuilder.build(COMPONENT, "AAIEventSendToDmaap");
				
				this.adp.getMRBatchingPublisher().send(jo.getString("message"));
			
			}catch (IOException e) {
				if (e instanceof java.net.SocketException) {
					if (e.getMessage().contains("Connection reset")) {
					} else {
						ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_7304",
								"Error reaching DMaaP to send event. " + aaiEvent);
						aaiLogger.error(errorObject, logline, e);
					}
				} else {
					ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_7304",
							"Error reaching DMaaP to send event. " + aaiEvent);
					aaiLogger.error(errorObject, logline, e);
				}
			} catch (JMSException | JSONException e) {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject("AAI_7350",
						"Error parsing aaievent jsm message for sending to dmaap. " + jsmMessageTxt);
				aaiLogger.error(errorObject, logline, e);
			}
		}
	}
}
