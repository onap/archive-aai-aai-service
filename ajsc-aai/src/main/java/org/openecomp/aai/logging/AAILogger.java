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

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.slf4j.MDC;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;

/**
 * This class provides Logger methods for the AAI application
 */
@SuppressWarnings("rawtypes")
public class AAILogger {

	public EELFLogger logger;

	// private boolean mdcInitialized = false;

	/**
	 * Instantiates a new AAI logger.
	 *
	 * @param name the name
	 */
	public AAILogger(String name) {
		// if ( !mdcInitialized ) { address problem where host/ip not in most
		// logfile entries
		MDC.put("ERROR_CODE", "");
		MDC.put("ERROR_TEXT", "");
		if (MDC.get("hostaddress") == null) {
			mdcSetUp();
			// mdcInitialized = true;
		}
		this.logger = EELFManager.getInstance().getLogger(name);
	}

	/**
	 * Instantiates a new AAI logger.
	 *
	 * @param clazz the clazz
	 */
	public AAILogger(Class clazz) {
		this(clazz.getSimpleName());
	}

	/**
	 * Mdc set up.
	 */
	private void mdcSetUp() {
		InetAddress ip;
		String hostname;

		MDC.put("hostname", "");
		MDC.put("hostaddress", "");
	
		try {
			ip = InetAddress.getLocalHost();
			if (ip != null) {
				hostname = ip.getCanonicalHostName();
				if (hostname != null)
					MDC.put("hostname", hostname);
				MDC.put("hostaddress", ip.getHostAddress());
			}
			// System.out.println("MDC setup " + MDC.get("hostname"));
		} catch (UnknownHostException e) {

			e.printStackTrace();

		}
	}

	/**
	 * Method isInfoEnabled.
	 * 
	 * @return boolean
	 */
	public boolean isInfoEnabled() {
		return logger.isInfoEnabled();
	}

	/**
	 * Method isDebugEnabled.
	 * 
	 * @return boolean
	 */
	public boolean isDebugEnabled() {
		return logger.isDebugEnabled();
	}

	/**
	 * Method debug.
	 * 
	 * @param logline
	 *            LogLine
	 * @param text
	 *            String
	 */
	public void debug(LogLine logline, String text) {
		if (isDebugEnabled()) {
			logline.setLevel("DEBUG");
			logline.setUserContributed(text);
			String msg = logline.getLine(false).replaceAll("\\n", "^");// make it
																	// more
																	// readable
																	// by
																	// replacing
																	// newlines
			logger.debug(msg);
		}
	}

	/**
	 * Method debug.
	 * 
	 * @param logline
	 *            LogLine
	 * @param text
	 *            String
	 * @param t
	 *            Throwable
	 */
	public void debug(LogLine logline, String text, Throwable t) {
		if (isDebugEnabled()) {
			logline.setLevel("DEBUG");
			logline.add("db", text);
			String msg = logline.getLine(false).replaceAll("\\n", "^");// make it
																	// more
																	// readable
																	// by
																	// replacing
																	// newlines
			logger.debug(msg, t);

			Throwable nestedT = getNestedThrowable(t);
			if (nestedT != null) {
				logger.debug(logline + "More info on previous error: ", nestedT);
			}
		}
	}
	
	/**
	 * Method audit.
	 * 
	 * @param logline
	 *            LogLine
	 */
	public void audit(LogLine logline) {
		if (isInfoEnabled()) {
			logline.setLevel("INFO");
			String msg = logline.getLine(true);
			logger.info(msg);
		}
	}

	/**
	 * Method info.
	 * 
	 * @param logline
	 *            LogLine
	 * @param success
	 *            Boolean true or false
	 * @param errorCode 
	 * 	 		"0" for success=true and a valid "AAI_*" error code from error.properties for success=false
	 *           ex. 			aaiLogger.info(logline, false, "AAI_7402", e);
	 *                          aaiLogger.info(logline, true, "0");
	 *                          aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString(), e);
	 */
	public void info(LogLine logline, Boolean success, String errorCode) {
		info(logline, success, errorCode, null);
	}

	/**
	 * Method info.
	 * 
	 * @param logline
	 *            LogLine
	 * @param success
	 *            Boolean true or false
	 * @param errorCode 
	 * 			"0" for success=true and a valid "AAI_*" error code from error.properties for success=false
	 *           ex. 			aaiLogger.info(logline, false, "AAI_7402", e);
	 *                          aaiLogger.info(logline, true, "0");
	 *                          aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString(), e);
	 * @param t
	 *            Throwable
	 */
	public void info(LogLine logline, Boolean success, String errorCode, Throwable t) {
		if (isInfoEnabled()) {
			logline.setLevel("INFO");
			if (errorCode.equals("0")) {
				logline.setEc(errorCode);
				//Set Response Description to "Success" if error code is 0.
				if (success){
					logline.setEt("Success");
				}
			}
			else {
				ErrorObject errorObject = ErrorLogHelper.getErrorObject(errorCode);
				logline.setEc(errorObject.getErrorCodeString());
				logline.setEt(errorObject.getErrorText()); 
			}
			
			if (t != null) {
				Throwable nestedT = getNestedThrowable(t);
				if (nestedT != null) {
					logline.add("info", nestedT.getStackTrace().toString());
				}
			}
			String msg = logline.finish(success);
			logger.info(msg);
		}
	}

	/**
	 * Method error.
	 * 
	 * @param errorObject
	 *            ErrorObject
	 * @param logline
	 *            LogLine
	 */
	@Deprecated
	public void error(ErrorObject errorObject, LogLine logline) {
		error(errorObject, logline, null);
	}

	/**
	 * Method error.
	 * 
	 * @param errorObject
	 *            ErrorObject
	 * @param logline
	 *            LogLine
	 * @param t
	 *            Throwable
	 */
	public void error(ErrorObject errorObject, LogLine logline, Throwable t) {

		String errorSeverity = errorObject.getSeverity();
		if (errorSeverity.equalsIgnoreCase("WARN"))
			logline.setLevel("WARN");
		else if (errorSeverity.equalsIgnoreCase("ERROR"))
			logline.setLevel("ERROR");
		else if (errorSeverity.equalsIgnoreCase("FATAL"))
			logline.setLevel("FATAL");

		String errorMessage = errorObject.getErrorText() + ":"
				+ errorObject.getRESTErrorCode() + ":" + errorObject.getHTTPResponseCode();
		if (errorObject.getDetails() != null)
			errorMessage += ":" + errorObject.getDetails();
		errorMessage = errorMessage.replaceAll("\\n", "^");
		MDC.put("ERROR_CODE", errorObject.getErrorCodeString());
		MDC.put("ERROR_TEXT", errorMessage);
		try {
			if (t != null)
				errorMessage += ":" + getStackTop(t);
		} catch (AAIException e) {
			errorMessage += ": unable to get stack trace, " + e.getMessage() + ":" + e.getErrorObject().getErrorText();
		}
		errorMessage = errorMessage.replaceAll("\\n", "^");

		logline.setEc(errorObject.getErrorCodeString());
		logline.setEt(errorMessage);
		
		//Set status code correctly
		logline.setSs("ERROR");


		if (errorSeverity.equalsIgnoreCase("WARN"))
			warn(logline.getLine(false));
		else if (errorSeverity.equalsIgnoreCase("ERROR"))
			error(logline.getLine(false));
		else if (errorSeverity.equalsIgnoreCase("FATAL"))
			fatal(logline.getLine(false));
		// logNestedException(Level.DEBUG, errorMessage, t);
	}

	/**
	 * Method warn.
	 * 
	 * @param logline
	 *            String
	 */
	private void warn(String logline) {
		logger.warn(logline);
	}

	/**
	 * Method error.
	 * 
	 * @param logline
	 *            String
	 */
	private void error(String logline) {
		logger.error(logline);
	}

	/**
	 * Method fatal.
	 * 
	 * @param logline
	 *            String
	 */
	private void fatal(String logline) {
		logger.error(logline);
	}

	// /**
	// * Method logNestedException.
	// *
	// * @param level
	// * Level
	// * @param logline
	// * String
	// * @param t
	// * Throwable
	// */
	// private void logNestedException(Level level, String logline, Throwable t)
	// {
	// if (null == t) {
	// return;
	// }
	//
	// try {
	// Class tC = t.getClass();
	// Method[] mA = tC.getMethods();
	// Method nextThrowableMethod = null;
	//
	// for (int i = 0; i < mA.length; i++) {
	// if (("getCause".equals(mA[i].getName())) ||
	// "getRootCause".equals(mA[i].getName())
	// || "getNextException".equals(mA[i].getName()) ||
	// "getException".equals(mA[i].getName())) {
	// // check param types
	// Class[] params = mA[i].getParameterTypes();
	//
	// if ((null == params) || (0 == params.length)) {
	// // just found the getter for the nested throwable
	// nextThrowableMethod = mA[i];
	//
	// break; // no need to search further
	// }
	// }
	// }
	//
	// if (null != nextThrowableMethod) {
	// // get the nested throwable and log it
	// Throwable nextT = (Throwable) nextThrowableMethod.invoke(t, new
	// Object[0]);
	//
	// if (null != nextT) {
	// this.logger.log(AAICLASS, level, logline + "More info on previous error:
	// ", nextT);
	// }
	// }
	// } catch (Exception e) {
	// // do nothing
	// }
	// }

	/**
	 * This method returns the nested throwable of the given throwable.
	 *
	 * @param t            Throwable
	 * @return the nested throwable
	 */
	private Throwable getNestedThrowable(Throwable t) {
		if (null == t) {
			return null;
		}

		try {
			Class tC = t.getClass();
			Method[] mA = tC.getMethods();
			Method nextThrowableMethod = null;

			for (int i = 0; i < mA.length; i++) {
				if (("getCause".equals(mA[i].getName())) || "getRootCause".equals(mA[i].getName())
						|| "getNextException".equals(mA[i].getName()) || "getException".equals(mA[i].getName())) {
					// check param types
					Class[] params = mA[i].getParameterTypes();

					if ((null == params) || (0 == params.length)) {
						// just found the getter for the nested throwable
						nextThrowableMethod = mA[i];

						break; // no need to search further
					}
				}
			}

			if (null != nextThrowableMethod) {
				// get the nested throwable and log it
				return (Throwable) nextThrowableMethod.invoke(t, new Object[0]);

			}
		} catch (Exception e) {
			// do nothing
		}

		return null;
	}

	/**
	 * Gets the stack top.
	 *
	 * @param e the e
	 * @return the stack top
	 * @throws NumberFormatException the number format exception
	 * @throws AAIException the AAI exception
	 */
	public String getStackTop(Throwable e) throws NumberFormatException, AAIException {
		StringBuffer stackMessage = new StringBuffer();
		int maxStackTraceEntries = Integer.valueOf(AAIConfig.get(AAIConstants.LOGGING_MAX_STACK_TRACE_ENTRIES));
		if (e != null) {
			Throwable rootCause = ExceptionUtils.getRootCause(e);
			if (rootCause != null) {
				stackMessage.append("root cause=" + ExceptionUtils.getRootCause(e));
				StackTraceElement[] elements = rootCause.getStackTrace();
				int i = 0;
				for (StackTraceElement element : elements) {
					if (i < maxStackTraceEntries) {
						stackMessage.append(" ClassName- ");
						stackMessage.append(element.getClassName());
						stackMessage.append(" :LineNumber- ");
						stackMessage.append(element.getLineNumber());
						stackMessage.append(" :MethodName- ");
						stackMessage.append(element.getMethodName());
					}
					i++;
				}
			} else if (e.getCause() != null) {
				stackMessage.append("cause=" + e.getCause());
				StackTraceElement[] elements = e.getCause().getStackTrace();
				int i = 0;
				for (StackTraceElement element : elements) {
					if (i < maxStackTraceEntries) {
						stackMessage.append(" ClassName- ");
						stackMessage.append(element.getClassName());
						stackMessage.append(" :LineNumber- ");
						stackMessage.append(element.getLineNumber());
						stackMessage.append(" :MethodName- ");
						stackMessage.append(element.getMethodName());
					}
					i++;
				}
			} else if (e.getStackTrace() != null) {
				stackMessage.append("ex=" + e.toString());
				StackTraceElement[] elements = e.getStackTrace();
				int i = 0;
				for (StackTraceElement element : elements) {
					if (i < maxStackTraceEntries) {
						stackMessage.append(" ClassName- ");
						stackMessage.append(element.getClassName());
						stackMessage.append(" :LineNumber- ");
						stackMessage.append(element.getLineNumber());
						stackMessage.append(" :MethodName- ");
						stackMessage.append(element.getMethodName());
					}
					i++;
				}
			}
		}
		return stackMessage.toString();
	}
}
