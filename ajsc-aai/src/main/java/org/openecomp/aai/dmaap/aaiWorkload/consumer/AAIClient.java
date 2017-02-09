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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.json.JSONObject;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class AAIClient {

	private Client restClient = null;
	private int baseTimeout = 5000;
	private int numRetriesOnTimeout = 0;

	public AAIClient() throws KeyManagementException, NoSuchAlgorithmException, AAIException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {

		this.restClient = initClient();
	}

	/**
	 * 
	 * @param baseTimeout
	 *            min value of 5000ms
	 * @param numRetriesOnTimeout
	 *            max value 5 (will default to 4 for any value higher
	 * @throws KeyManagementException
	 * @throws NoSuchAlgorithmException
	 * @throws AAIException
	 * @throws UnrecoverableKeyException
	 * @throws KeyStoreException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public AAIClient(int baseTimeout, int numRetriesOnTimeout)
			throws KeyManagementException, NoSuchAlgorithmException, AAIException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		this.restClient = initClient();
		if (baseTimeout > 0) {
			this.baseTimeout = baseTimeout;
		}
		if (numRetriesOnTimeout > 0) {
			this.numRetriesOnTimeout = numRetriesOnTimeout;
		}
		if (this.numRetriesOnTimeout > 5) {
			this.numRetriesOnTimeout = 5;
		}
	}

	/**
	 * @return
	 * @throws AAIException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 * @throws KeyStoreException
	 * @throws IOException
	 * @throws CertificateException
	 * @throws UnrecoverableKeyException
	 */
	private Client initClient()
			throws AAIException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, IOException, UnrecoverableKeyException {
		ClientConfig config = new DefaultClientConfig();

		SSLContext ctx = null;

		String truststore_path = AAIConstants.AAI_HOME_ETC_AUTH + AAIConfig.get(AAIConstants.AAI_TRUSTSTORE_FILENAME);
		String truststore_password = AAIConfig.get(AAIConstants.AAI_TRUSTSTORE_PASSWD);
		String keystore_path = AAIConstants.AAI_HOME_ETC_AUTH + AAIConfig.get(AAIConstants.AAI_KEYSTORE_FILENAME);
		String keystore_password = AAIConfig.get(AAIConstants.AAI_KEYSTORE_PASSWD);

		System.setProperty("javax.net.ssl.trustStore", truststore_path);
		System.setProperty("javax.net.ssl.trustStorePassword", truststore_password);
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
			public boolean verify(String string, SSLSession ssls) {
				return true;
			}
		});

		ctx = SSLContext.getInstance("TLSv1.2");
		KeyManagerFactory kmf = null;
		kmf = KeyManagerFactory.getInstance("SunX509");
		FileInputStream fin = new FileInputStream(keystore_path);
		KeyStore ks = KeyStore.getInstance("PKCS12");
		char[] pwd = keystore_password.toCharArray();
		ks.load(fin, pwd);
		kmf.init(ks, pwd);

		ctx.init(kmf.getKeyManagers(), null, null);
		config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(new HostnameVerifier() {
			@Override
			public boolean verify(String s, SSLSession sslSession) {
				return true;
			}
		}, ctx));

		return Client.create(config);
	}

	public ClientResponse put(String restUri, JSONObject eventBody, String sourceName) throws Exception {

		int tries = 1;
		ClientResponse response;
		WebResource webResource;
		while (true) {
			try {
				this.restClient.setConnectTimeout(tries * this.baseTimeout);
				webResource = restClient.resource(AAIConfig.get("aai.server.url") + restUri);
				response = webResource.accept("application/json").header("X-FromAppId", "aaiWorkload-dmaap-" + sourceName).header("X-TransactionId", UUID.randomUUID())
						.type("application/json").put(ClientResponse.class, eventBody.toString());
				return response;
			} catch (Exception e) {
				if (e instanceof SocketTimeoutException && tries < this.numRetriesOnTimeout + 1) {
					tries++;
				} else {
					throw e;

				}
			}
		}

	}

}
