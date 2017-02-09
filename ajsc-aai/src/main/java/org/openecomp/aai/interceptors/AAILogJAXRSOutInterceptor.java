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

package org.openecomp.aai.interceptors;

import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.LoggingMessage;
import org.apache.cxf.io.CacheAndWriteOutputStream;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.io.CachedOutputStreamCallback;
import org.apache.cxf.jaxrs.interceptor.JAXRSOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
//import org.apache.log4j.MDC;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.dmaap.AAIDmaapEventJMSProducer;
import org.openecomp.aai.domain.notificationEvent.NotificationEvent;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.openecomp.aai.util.PojoUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

// right after the request is complete, there may be content
public class AAILogJAXRSOutInterceptor extends JAXRSOutInterceptor {

	protected final String COMPONENT = "aairest";
	protected final String CAMEL_REQUEST = "CamelHttpUrl";

	private HConnection hConnection = null;
	private org.apache.hadoop.conf.Configuration config = null;

	@Autowired
	private ApplicationContext appContext;
	private AAIDmaapEventJMSProducer springJmsProducer;

	/**
	 * {@inheritDoc}
	 */
	public void handleMessage(Message message) {

		AAILogger aaiLogger = new AAILogger(AAILogJAXRSOutInterceptor.class.getName());
		LogLine logline = new LogLine();

		String fullId = (String) message.getExchange().get(LoggingMessage.ID_KEY);

		Map<String, List<String>> headers = CastUtils.cast((Map<?, ?>) message.get(Message.PROTOCOL_HEADERS));
		if (headers == null) {
			headers = new HashMap<String, List<String>>();
		}

		headers.put("X-AAI-TXID", Collections.singletonList(fullId));
		message.put(Message.PROTOCOL_HEADERS, headers);

		Message outMessage = message.getExchange().getOutMessage();
		final OutputStream os = outMessage.getContent(OutputStream.class);
		if (os == null) {
			return;
		}

		if (message.getExchange().containsKey("AAI_LOGGING_HBASE_ENABLED") && hConnection == null) {
			try {
				config = HBaseConfiguration.create();

				if (config == null) {
					aaiLogger.debug(logline, "AAITxnLog: Default Constructor: can't create HBase configuration");
					return;
				}

				config.set(AAIConstants.ZOOKEEPER_ZNODE_PARENT,
						AAIConfig.get(AAIConstants.HBASE_ZOOKEEPER_ZNODE_PARENT));
				config.set(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_QUORUM,
						AAIConfig.get(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_QUORUM));
				config.set(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_CLIENTPORT,
						AAIConfig.get(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_CLIENTPORT));

				hConnection = HConnectionManager.createConnection(config);
			} catch (Exception e) {
				aaiLogger.debug(logline, "Cannot establish connection to hbase", e);
				return;
			}
		}

//		if (this.springJmsProducer == null) {
//			this.springJmsProducer = (AAIDmaapEventJMSProducer) this.appContext.getBean("messageProducer");
//		}
		// we only want to register the callback if there is good reason for it.
		if (message.getExchange().containsKey("AAI_LOGGING_HBASE_ENABLED")
				|| message.getExchange().containsKey("AAI_LOGGING_TRACE_ENABLED")) {

			// FINEGRAINaaiLogger.debug(logline, "Registering callback for
			// logging");

			final CacheAndWriteOutputStream newOut = new CacheAndWriteOutputStream(os);
			message.setContent(OutputStream.class, newOut);
			newOut.registerCallback(new LoggingCallback(message, os));
		}

	}

	class LoggingCallback implements CachedOutputStreamCallback {

		private final Message message;
		private final OutputStream origStream;

		public LoggingCallback(final Message msg, final OutputStream os) {
			this.message = msg;
			this.origStream = os;
		}

		public void onFlush(CachedOutputStream cos) {

		}

		public void onClose(CachedOutputStream cos) {
			// LoggingMessage buffer = setupBuffer(message);
			//
			// String formattedMessage = formatLoggingMessage(buffer);
			AAILogger aaiLogger = new AAILogger(AAILogJAXRSOutInterceptor.class.getName());
			AAILogger auditLogger = new AAILogger("org.openecomp.aai.AUDITLOGGER");
			LogLine logline = new LogLine();

			if (!message.getExchange().containsKey("AAI_LOGGING_HBASE_ENABLED")
					&& !message.getExchange().containsKey("AAI_LOGGING_TRACE_ENABLED")) {
				// FINEGRAINaaiLogger.debug(logline, "Skipping the callback
				// interceptor, logging disabled");
				return;
			}

			// this function gets all the stuff that's closest to the wire and
			// actually
			// logs to hbase

			String fullId = (String) message.getExchange().get(LoggingMessage.ID_KEY);

			Message inMessage = message.getExchange().getInMessage();
			String transId = null;
			String fromAppId = null;

			Map<String, List<String>> headersList = CastUtils.cast((Map<?, ?>) inMessage.get(Message.PROTOCOL_HEADERS));
			if (headersList != null) {
				List<String> xt = headersList.get("X-TransactionId");
				if (xt != null) {
					for (String transIdValue : xt) {
						transId = transIdValue;
					}
				}
				List<String> fa = headersList.get("X-FromAppId");
				if (fa != null) {
					for (String fromAppIdValue : fa) {
						fromAppId = fromAppIdValue;
					}
				}
			}

			logline.init(COMPONENT, transId, fromAppId, "interceptOut");
			// FINEGRAINaaiLogger.debug(logline, "Logging callback hbase
			// transactionId: " + fullId);
			String httpMethod = (String) inMessage.get(Message.HTTP_REQUEST_METHOD);

			String uri = (String) inMessage.get(CAMEL_REQUEST);
			String fullUri = uri;
			if (uri != null) {
				String query = (String) message.get(Message.QUERY_STRING);
				if (query != null) {
					fullUri = uri + "?" + query;
				}
			}

			String request = (String) message.getExchange().get(fullId + "_REQUEST");

			Message outMessage = message.getExchange().getOutMessage();

			final LoggingMessage buffer = new LoggingMessage("OUTMessage", fullId);

			// should we check this, and make sure it's not an error?
			Integer responseCode = (Integer) outMessage.get(Message.RESPONSE_CODE);
			if (responseCode == null) {
				responseCode = 200; // this should never happen, but just in
									// case we don't get one
			}
			buffer.getResponseCode().append(responseCode);

			String encoding = (String) outMessage.get(Message.ENCODING);

			if (encoding != null) {
				buffer.getEncoding().append(encoding);
			}

			String ct = (String) outMessage.get(Message.CONTENT_TYPE);
			if (ct != null) {
				buffer.getContentType().append(ct);
			}

			Object headers = outMessage.get(Message.PROTOCOL_HEADERS);
			if (headers != null) {
				buffer.getHeader().append(headers);
			}

			Boolean ss = false;
			if (responseCode >= 200 && responseCode <= 299) {
				ss = true;
			}
			String response = buffer.toString();

			// this should have been set by the in interceptor
			String rqstTm = (String) message.getExchange().get("AAI_RQST_TM");

			// just in case it wasn't, we'll put this here. not great, but it'll
			// have a val.
			if (rqstTm == null) {
				rqstTm = genDate(aaiLogger, logline);
			}

			String respTm = genDate(aaiLogger, logline);

			// TODO: make sure these aren't null, maybe make that primitive Int
			// an object type
			// TODO: pretty print json. maybe i should tak
			// final String fromAppId =
			// (String)message.getExchange().get("LOG_TX_fromAppId");
			// final String httpMethod =
			// (String)message.getExchange().get("LOG_TX_httpMethod");
			// final String transId =
			// (String)message.getExchange().get("LOG_TX_transId");
			// final String fullUri =
			// (String)message.getExchange().get("LOG_TX_fullUri");
			// final String rqstTm =
			// (String)message.getExchange().get("LOG_TX_rqstTm");
			// final String respTm =
			// (String)message.getExchange().get("LOG_TX_respTm");
			// final String request =
			// (String)message.getExchange().get("LOG_TX_request");
			// final int responseCode =
			// (int)message.getExchange().get("LOG_TX_responseCode");
			// final String response =
			// (String)message.getExchange().get("LOG_TX_response");
			// final String fullId =
			// (String)message.getExchange().get("LOG_TX_fullId");

			try {
				String actualRequest = request;
				StringBuilder builder = new StringBuilder();
				cos.writeCacheTo(builder, 100000);
				// here comes my xml:
				String payload = builder.toString();

				String actualResponse = response;
				if (payload == null) {
					// FINEGRAINaaiLogger.debug(logline,
					// "AAILogJAXRSOutInterceptor: no payload");
				} else {
					actualResponse = response + payload;
				}

				// we only log to AAI log if it's eanbled in the config props
				// file
				if (message.getExchange().containsKey("AAI_LOGGING_TRACE_ENABLED")) {

					// FINEGRAINaaiLogger.debug(logline, "Trace is on, going to
					// log to aai log");
					if (message.getExchange().containsKey("AAI_LOGGING_TRACE_LOGREQUEST")) {

						// strip newlines from request
						String traceRequest = actualRequest;
						traceRequest = traceRequest.replace("\n", " ");
						traceRequest = traceRequest.replace("\r", "");
						traceRequest = traceRequest.replace("\t", "");
						aaiLogger.debug(logline, traceRequest);
					}
					if (message.getExchange().containsKey("AAI_LOGGING_TRACE_LOGRESPONSE")) {
						// strip newlines from response
						String traceResponse = actualResponse;
						traceResponse = traceResponse.replace("\n", " ");
						traceResponse = traceResponse.replace("\r", "");
						traceResponse = traceResponse.replace("\t", "");

						aaiLogger.debug(logline, traceResponse);
					}
				}

				// we only log to HBASE if it's enabled in the config props file
				// TODO: pretty print XML/JSON. we might need to get the payload
				// and envelope seperately
				if (message.getExchange().containsKey("AAI_LOGGING_HBASE_ENABLED")) {
					if (!message.getExchange().containsKey("AAI_LOGGING_HBASE_LOGREQUEST")) {
						actualRequest = "loggingDisabled";
					}
					if (!message.getExchange().containsKey("AAI_LOGGING_HBASE_LOGRESPONSE")) {
						actualResponse = "loggingDisabled";
					}

					// only log notification event on success
					NotificationEvent ne = new NotificationEvent();
					boolean hasNotificationEvent = false;
					boolean notificationEventIsDynamic = false;
					DynamicJAXBContext notificationJaxbContext = null;
					DynamicEntity notificationEventEntity = null;

					if (responseCode >= 200 && responseCode <= 299) {
						if (message.getExchange().containsKey("NOTIFICATION_EVENT")) {
							hasNotificationEvent = true;

							if (message.getExchange().containsKey("NOTIFICATION_EVENT_TYPE")) {
								String notificationEventType = (String) message.getExchange()
										.get("NOTIFICATION_EVENT_TYPE");
								if (notificationEventType.equals("dynamic")) {
									notificationJaxbContext = (DynamicJAXBContext) message.getExchange()
											.get("NOTIFICATION_JAXB_CONTEXT");
									notificationEventIsDynamic = true;
									notificationEventEntity = (DynamicEntity) message.getExchange()
											.get("NOTIFICATION_EVENT");
								}
							} else {
								ne = (NotificationEvent) message.getExchange().get("NOTIFICATION_EVENT");
							}
						}
					}

					logline.add("action", httpMethod);
					logline.add("urlin", fullUri);
					logline.add("HbTransId", fullId);

					if (notificationEventIsDynamic) {
						logTransaction(fromAppId, transId, fullId, httpMethod, fullUri, rqstTm, respTm, actualRequest,
								actualResponse, responseCode, hasNotificationEvent, notificationEventEntity,
								notificationJaxbContext);
					} else {
						logTransaction(fromAppId, transId, fullId, httpMethod, fullUri, rqstTm, respTm, actualRequest,
								actualResponse, responseCode, hasNotificationEvent, ne);
					}
				}
			} catch (Exception ex) {
				// ignore
			}

			message.setContent(OutputStream.class, origStream);
			aaiLogger.info(logline, true, "0");

			if (message.getExchange().containsKey("AUDIT_LOGLINE")) {
				LogLine auditLogline = (LogLine) message.getExchange().get("AUDIT_LOGLINE");
				if (MDC.get("ERROR_CODE") != null)
					auditLogline.setEc(MDC.get("ERROR_CODE"));
				if (MDC.get("ERROR_TEXT") != null)
					auditLogline.setEt(MDC.get("ERROR_TEXT"));
				auditLogline.add("HTTP Response Code", responseCode.toString());
				auditLogline.finish(ss);
				auditLogger.audit(auditLogline);
			}
		}
	}

	protected String genDate(AAILogger aaiLogger, LogLine logline) {
		Date date = new Date();
		DateFormat formatter = null;
		try {
			formatter = new SimpleDateFormat(AAIConfig.get(AAIConstants.HBASE_TABLE_TIMESTAMP_FORMAT));
		} catch (AAIException ex) {
			logline.add("Property", AAIConstants.HBASE_TABLE_TIMESTAMP_FORMAT);
			aaiLogger.error(ex.getErrorObject(), logline);
		} finally {
			if (formatter == null) {
				formatter = new SimpleDateFormat("YYMMdd-HH:mm:ss:SSS");
			}
		}
		return formatter.format(date);
	}

	/**
	 * Log transaction.
	 *
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param tId the t id
	 * @param action the action
	 * @param input the input
	 * @param rqstTm the rqst tm
	 * @param respTm the resp tm
	 * @param request the request
	 * @param response the response
	 * @param status the status
	 * @param hasNotificationEvent the has notification event
	 * @param ne the ne
	 */
	/*  ---------------- Log Transaction into HBase (for Apache CXF Interceptors) --------------------- */
	public void logTransaction(String fromAppId, String transId, String tId, String action, String input, String rqstTm,
			String respTm, String request, String response, int status, boolean hasNotificationEvent,
			NotificationEvent ne) throws JMSException {
		String hbtid = putTransaction(tId, String.valueOf(status), rqstTm, respTm, fromAppId + ":" + transId, input,
				action, request, response, hasNotificationEvent, ne);
	}

	/**
	 * Log transaction.
	 *
	 * @param fromAppId the from app id
	 * @param transId the trans id
	 * @param tId the t id
	 * @param action the action
	 * @param input the input
	 * @param rqstTm the rqst tm
	 * @param respTm the resp tm
	 * @param request the request
	 * @param response the response
	 * @param status the status
	 * @param hasNotificationEvent the has notification event
	 * @param ne the ne
	 * @param notificationJaxbContext the notification jaxb context
	 */
	/*  ---------------- Log Transaction into HBase (for Apache CXF Interceptors) --------------------- */
	public void logTransaction(String fromAppId, String transId, String tId, String action, String input, String rqstTm,
			String respTm, String request, String response, int status, boolean hasNotificationEvent, DynamicEntity ne,
			DynamicJAXBContext notificationJaxbContext) throws JMSException {
		String hbtid = putTransaction(tId, String.valueOf(status), rqstTm, respTm, fromAppId + ":" + transId, input,
				action, request, response, hasNotificationEvent, ne, notificationJaxbContext);
	}

	/**
	 * Put transaction.
	 *
	 * @param tid the tid
	 * @param status the status
	 * @param rqstTm the rqst tm
	 * @param respTm the resp tm
	 * @param srcId the src id
	 * @param rsrcId the rsrc id
	 * @param rsrcType the rsrc type
	 * @param rqstBuf the rqst buf
	 * @param respBuf the resp buf
	 * @param hasNotificationEvent the has notification event
	 * @param ne the ne
	 * @return the string
	 */
	public String putTransaction(String tid, String status, String rqstTm, String respTm, String srcId, String rsrcId,
			String rsrcType, String rqstBuf, String respBuf, boolean hasNotificationEvent, NotificationEvent ne) {
		AAILogger aaiLogger = new AAILogger(AAILogJAXRSOutInterceptor.class.getName());
		LogLine logline = new LogLine();
		String tm = null;
		String fromAppId = srcId.substring(0, srcId.indexOf(':'));
		String transId = srcId.substring(srcId.indexOf(':') + 1);

		logline.init(COMPONENT, transId, fromAppId, "putTransaction");
		// FINEGRAINaaiLogger.debug(logline, "In put: storing hbase config
		// file...");
		Exception ex = null;

		if (tid == null || "".equals(tid)) {
			Date date = new Date();
			DateFormat formatter = null;
			try {
				formatter = new SimpleDateFormat(AAIConfig.get(AAIConstants.HBASE_TABLE_TIMESTAMP_FORMAT));
			} catch (Exception e) {
				formatter = new SimpleDateFormat("YYYYMMdd-HH:mm:ss:SSS");
			}
			tm = formatter.format(date);
			tid = tm + "-";
		}
		// String htid = tid + rsrcType; // orig
		// String htid = tid + srcId; //final version?
		String htid = tid;// + srcId + "_" + rsrcType; // use this one for now
		// FINEGRAINaaiLogger.debug(logline, "In put...: htid="+htid);
		// FINEGRAINaaiLogger.debug(logline, "tid: " + tid + ", srcId: " + srcId
		// + " rsrcType: " + rsrcType);

		if (rqstTm == null || "".equals(rqstTm)) {
			rqstTm = tm;
		}

		if (respTm == null || "".equals(respTm)) {
			respTm = tm;
		}

		try {
			HTableInterface table = hConnection.getTable(AAIConfig.get(AAIConstants.HBASE_TABLE_NAME));

			Put p = new Put(Bytes.toBytes(htid));

			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("tid"), Bytes.toBytes(tid));
			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("status"), Bytes.toBytes(status));
			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("rqstDate"), Bytes.toBytes(rqstTm));
			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("respDate"), Bytes.toBytes(respTm));
			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("sourceId"), Bytes.toBytes(srcId));

			p.add(Bytes.toBytes("resource"), Bytes.toBytes("resourceId"), Bytes.toBytes(rsrcId));
			p.add(Bytes.toBytes("resource"), Bytes.toBytes("resourceType"), Bytes.toBytes(rsrcType));

			p.add(Bytes.toBytes("payload"), Bytes.toBytes("rqstBuf"), Bytes.toBytes(rqstBuf));
			p.add(Bytes.toBytes("payload"), Bytes.toBytes("respBuf"), Bytes.toBytes(respBuf));

			/*
			 * Once you've adorned your Put instance with all the updates you
			 * want to make, to commit it do the following
			 */
			table.put(p);
			table.flushCommits();
			table.close();

			return htid;
		} catch (Exception e) {
			aaiLogger.debug(logline, "AAITxnLog: put: Exception=", e);
			aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_4000", "Exception updating HBase:"), logline, e);
			return htid;
		} finally {
			if (ex == null)
				aaiLogger.info(logline, true, "0");
			else
				aaiLogger.info(logline, false, "AAI_4000");
		}
	}

	/**
	 * Put transaction.
	 *
	 * @param tid the tid
	 * @param status the status
	 * @param rqstTm the rqst tm
	 * @param respTm the resp tm
	 * @param srcId the src id
	 * @param rsrcId the rsrc id
	 * @param rsrcType the rsrc type
	 * @param rqstBuf the rqst buf
	 * @param respBuf the resp buf
	 * @param hasNotificationEvent the has notification event
	 * @param ne the ne
	 * @param notificationJaxbContext the notification jaxb context
	 * @return the string
	 */
	public String putTransaction(String tid, String status, String rqstTm, String respTm, String srcId, String rsrcId,
			String rsrcType, String rqstBuf, String respBuf, boolean hasNotificationEvent, DynamicEntity ne,
			DynamicJAXBContext notificationJaxbContext) {
		AAILogger aaiLogger = new AAILogger("AAILogJAXRSOutInterceptor");
		LogLine logline = new LogLine();
		String tm = null;
		String fromAppId = srcId.substring(0, srcId.indexOf(':'));
		String transId = srcId.substring(srcId.indexOf(':') + 1);

		logline.init(COMPONENT, transId, fromAppId, "putTransaction");
		Exception ex = null;

		// FINEGRAINaaiLogger.debug(logline, "In put: storing hbase config
		// file...");

		if (tid == null || "".equals(tid)) {
			Date date = new Date();
			DateFormat formatter = null;
			try {
				formatter = new SimpleDateFormat(AAIConfig.get(AAIConstants.HBASE_TABLE_TIMESTAMP_FORMAT));
			} catch (Exception e) {
				formatter = new SimpleDateFormat("YYYYMMdd-HH:mm:ss:SSS");
			}
			tm = formatter.format(date);
			tid = tm + "-";
		}
		// String htid = tid + rsrcType; // orig
		// String htid = tid + srcId; //final version?
		String htid = tid;// + srcId + "_" + rsrcType; // use this one for now
		// FINEGRAINaaiLogger.debug(logline, "In put...: htid="+htid);
		// FINEGRAINaaiLogger.debug(logline, "tid: " + tid + ", srcId: " + srcId
		// + " rsrcType: " + rsrcType);

		if (rqstTm == null || "".equals(rqstTm)) {
			rqstTm = tm;
		}

		if (respTm == null || "".equals(respTm)) {
			respTm = tm;
		}

		try {
			HTableInterface table = hConnection.getTable(AAIConfig.get(AAIConstants.HBASE_TABLE_NAME));

			Put p = new Put(Bytes.toBytes(htid));

			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("tid"), Bytes.toBytes(tid));
			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("status"), Bytes.toBytes(status));
			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("rqstDate"), Bytes.toBytes(rqstTm));
			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("respDate"), Bytes.toBytes(respTm));
			p.add(Bytes.toBytes("transaction"), Bytes.toBytes("sourceId"), Bytes.toBytes(srcId));

			p.add(Bytes.toBytes("resource"), Bytes.toBytes("resourceId"), Bytes.toBytes(rsrcId));
			p.add(Bytes.toBytes("resource"), Bytes.toBytes("resourceType"), Bytes.toBytes(rsrcType));

			p.add(Bytes.toBytes("payload"), Bytes.toBytes("rqstBuf"), Bytes.toBytes(rqstBuf));
			p.add(Bytes.toBytes("payload"), Bytes.toBytes("respBuf"), Bytes.toBytes(respBuf));

			/*
			 * Once you've adorned your Put instance with all the updates you
			 * want to make, to commit it do the following
			 */
			table.put(p);
			table.flushCommits();
			table.close();

			return htid;
		} catch (Exception e) {
			ex = e;
			aaiLogger.debug(logline, "AAITxnLog: put: Exception=", e);
			aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_4000", "Exception updating HBase:"), logline, e);
			return htid;
		} finally {
			if (ex == null)
				aaiLogger.info(logline, true, "0");
			else
				aaiLogger.info(logline, false, "AAI_4000");
		}
	}

}
