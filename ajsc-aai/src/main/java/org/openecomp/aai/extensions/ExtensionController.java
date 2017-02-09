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

package org.openecomp.aai.extensions;

import java.lang.reflect.Method;

import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;

public class ExtensionController {

	/**
	 * Run extension.
	 *
	 * @param apiVersion the api version
	 * @param namespace the namespace
	 * @param resourceName the resource name
	 * @param methodName the method name
	 * @param aaiExtMap the aai ext map
	 * @param isPreExtension the is pre extension
	 * @throws AAIException the AAI exception
	 */
	public void runExtension(String apiVersion, String namespace,
			String resourceName, String methodName, AAIExtensionMap aaiExtMap,
			boolean isPreExtension) throws AAIException {
		String fromAppId = aaiExtMap.getFromAppId();
		String transId = aaiExtMap.getTransId();
		String extensionClassName = "org.openecomp.aai.extensions."
				+ apiVersion.toLowerCase() + "." + namespace + "."
				+ resourceName + "Extension";
		String defaultErrorCallback = resourceName + "ExtensionErrorCallback";
		LogLine eLogline = new LogLine();
		AAILogger aaiLogger = aaiExtMap.getAaiLogger();

		String configOption = "aai.extensions." + apiVersion.toLowerCase()
				+ "." + namespace.toLowerCase() + "."
				+ resourceName.toLowerCase() + ".enabled";
		
		try {
			String extensionEnabled = AAIConfig.get(configOption, "true");
			if (extensionEnabled.equalsIgnoreCase("false")) {
				return;
			}
			Class<?> clazz = Class.forName(extensionClassName);

			Method extension = clazz.getMethod(methodName,
					new Class[] { AAIExtensionMap.class });
			if (extension != null) {
				
				eLogline.init("runExtension", transId, fromAppId, methodName);
				
				Object ret = extension.invoke(clazz.newInstance(), aaiExtMap);
				// reset
				if (ret instanceof Integer) {
					//aaiLogger.debug(logline, methodName + "Returned "
					//		+ (int) ret + " " + aaiExtMap.getMessage());

					Exception e = null;
					if (isPreExtension == true) {
						e = aaiExtMap.getPreExtException();
					} else {
						e = aaiExtMap.getPostExtException();
					}

					boolean failOnError = true;
					if (isPreExtension == true) {
						failOnError = aaiExtMap.getPreExtFailOnError();
					} else {
						failOnError = aaiExtMap.getPostExtFailOnError();
					}

					if (e != null) {
						boolean handleException = true;
						if (isPreExtension == true) {
							if (aaiExtMap.getPreExtSkipErrorCallback() == true) { 
								handleException = false;
							}
						} else {
							if (aaiExtMap.getPostExtSkipErrorCallback() == true) { 
								handleException = false;
							}
						}
						if (handleException == true) {
							Method errorCallback = null;
							if (isPreExtension == true) {
								errorCallback = aaiExtMap
										.getPreExtErrorCallback();
							} else {
								errorCallback = aaiExtMap
										.getPostExtErrorCallback();
							}

							if (errorCallback != null) {
								//aaiLogger.debug(logline,
										//"Calling custom error callback: "
									//			+ errorCallback.getName());
								errorCallback.invoke(clazz.newInstance(),
										aaiExtMap);
							} else {
								Method defaultErrorCallbackExtension = clazz
										.getMethod(
												defaultErrorCallback,
												new Class[] { AAIExtensionMap.class });
								//aaiLogger.debug(
										//logline,
										//"Calling default error callback: "
											//	+ defaultErrorCallbackExtension
											//			.getName());
								defaultErrorCallbackExtension.invoke(
										clazz.newInstance(), aaiExtMap);
							}
						}
					}

					if (failOnError == true && e != null) {
						throw e;
					} else if (failOnError == false && e != null) { // in this
																	// case, we
																	// just note
																	// the error
																	// without
																	// stopping
						eLogline.add(methodName + " Message",
								aaiExtMap.getMessage());
						aaiLogger.info(eLogline, true, "0");
					} else { 
						aaiLogger.info(eLogline, true, "0");
					}
				}
			}
		} catch (ClassNotFoundException ex) {
			// do nothing, this is normal
			//aaiLogger.debug(logline, "Extension class not found in "
					//+ extensionClassName + ", skipping " + methodName + ".");
		} catch (NoSuchMethodException e) {
			// The extension class might exist but there is not a matching
			// method in the extension class
			//aaiLogger.debug(logline, methodName + " not found in " + extensionClassName + 
					//". This is likely normal, this message is for devs to debug extensions.");
		} catch (AAIException e) {
			aaiLogger.info(eLogline, false, e.getErrorObject().getErrorCodeString());
			throw e;
		} catch (Exception e) {
			aaiLogger.info(eLogline, false, "AAI_5105");
			throw new AAIException("AAI_5105", e);
		} 
	}
}
