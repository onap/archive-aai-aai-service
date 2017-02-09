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

import java.util.Properties;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;

import com.att.eelf.configuration.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;

public class GetResource {

	private static final String COMPONENT = "aairestctrl";
	private static final String FROMAPPID = "AAI-TOOLS";
	private static final String TRANSID = UUID.randomUUID().toString();

	private static final String USAGE_STRING = "Usage: getTool.sh <resource-path> \n + "
			+ "for example: resource-path for a particular customer is business/customers/customer/global-customer-id-1 \n";

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 */
	public static void main(String[] args) {

		// Set the logging file properties to be used by EELFManager
		Properties props = System.getProperties();
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, AAIConstants.AAI_GETRES_LOGBACK_PROPS);
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, AAIConstants.AAI_HOME_ETC_APP_PROPERTIES);

		AAILogger aaiLogger = new AAILogger(GetResource.class.getName());
		LogLine logline = new LogLine();
		logline.init(COMPONENT, TRANSID, FROMAPPID, "main");
		String url = null;
		try {
			if (args.length < 1) {
				System.out.println("Nothing to get or Insufficient arguments");
				System.out.println(USAGE_STRING);
				logline.add("msg", "Insufficient arguments");
				aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_7403"), logline, null);
				aaiLogger.info(logline, false, "AAI_7403");
				System.exit(1);
			} else {
				// Assume the config AAI_SERVER_URL has a last slash so remove
				// if
				// resource-path has it as the first char
				url = args[0].replaceFirst("^/", "");
				url = AAIConfig.get(AAIConstants.AAI_SERVER_URL) + url;

				logline.add("url", url);
				System.out.println("url=" + url);
				getNode(aaiLogger, logline, url);
				aaiLogger.info(logline, true, "0");
				System.exit(0);
			}
		} catch (AAIException e) {
			System.out.println("GET failed:" + e.getMessage());
			aaiLogger.error(e.getErrorObject(), logline, e);
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			System.exit(1);
		} catch (Exception e) {
			System.out.println("GET failed");
			aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_7402", e.getMessage()), logline, e);
			aaiLogger.info(logline, false, "AAI_7402");
			System.exit(1);
		}

	}

	/**
	 * Gets the node.
	 *
	 * @param aaiLogger
	 *            the aai logger
	 * @param logline
	 *            the logline
	 * @param url
	 *            the url
	 * @return the node
	 * @throws AAIException
	 *             the AAI exception
	 */

	public static void getNode(AAILogger aaiLogger, LogLine logline, String url) throws AAIException {
		try {

			String useBasicAuth = AAIConfig.get("aai.tools.enableBasicAuth");
			Client client = null;

			if (useBasicAuth != null && useBasicAuth.equals("true")) {
				client = HttpsAuthClient.getBasicAuthClient();
			} else {
				client = HttpsAuthClient.getTwoWaySSLClient();
			}

			System.out.println("Getting the resource...: " + url);

			ClientResponse cres = client.resource(url).header("X-TransactionId", TRANSID)
					.header("X-FromAppId", FROMAPPID).header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue()).accept("application/json").get(ClientResponse.class);

			logline.add("status", cres.getStatus());

			if (cres.getStatus() == 404) { // resource not found
				String msg = "Resource does not exist: " + cres.getStatus() + ":" + cres.getEntity(String.class);
				System.out.println("\n" + msg);
				logline.add("msg", msg);
				throw new AAIException("AAI_7404", "Resource does not exist");
			} else if (cres.getStatus() == 200) {
				String msg = cres.getEntity(String.class);
				ObjectMapper mapper = new ObjectMapper();
				Object json = mapper.readValue(msg, Object.class);
				String indented = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
				System.out.println(indented);
			} else {
				System.out.println(
						"Getting the Resource failed: " + cres.getStatus() + ":\n" + cres.getEntity(String.class));
				throw new AAIException("AAI_7402", "Error during GET");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
