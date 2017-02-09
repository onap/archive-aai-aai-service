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

/*
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ConnectionUtils;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
 */
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.openecomp.aai.domain.notificationEvent.NotificationEvent;
import org.openecomp.aai.domain.translog.TransactionLogEntries;
import org.openecomp.aai.domain.translog.TransactionLogEntry;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;

public class AAITxnLog  {

	private static final String COMPONENT = "aaitxnlog";
	protected static AAILogger aaiLogger = new AAILogger(AAITxnLog.class.getName());
	protected LogLine logline = new LogLine();

	private org.apache.hadoop.conf.Configuration config = null;
	private HTable table = null;
	private String tm = null;

	/**
	 * Instantiates a new AAI txn log.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 */
	public AAITxnLog(String transId, String fromAppId) {
		try {
			/* When you create a HBaseConfiguration, it reads in whatever you've set
	  		into your hbase-site.xml and in hbase-default.xml, as long as these can
	  		be found on the CLASSPATH */
			logline.init(COMPONENT, transId, fromAppId, "AAITxnLog() constructor");
			Date date = new Date();
			DateFormat formatter = new SimpleDateFormat(AAIConfig.get(AAIConstants.HBASE_TABLE_TIMESTAMP_FORMAT));
			tm = formatter.format(date);

			config = HBaseConfiguration.create();

			//org.apache.hadoop.conf.Configuration config = new org.apache.hadoop.conf.Configuration();
			//config = HBaseConfiguration.create();

			if (config == null) {
				aaiLogger.debug(logline, "AAITxnLog: Default Constructor: can't create HBase configuration");
				return;
			}		 

			config.set(AAIConstants.ZOOKEEPER_ZNODE_PARENT, AAIConfig.get(AAIConstants.HBASE_ZOOKEEPER_ZNODE_PARENT));
			config.set(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_QUORUM, AAIConfig.get(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_QUORUM));
			config.set(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_CLIENTPORT, AAIConfig.get(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_CLIENTPORT));

			/*
			InputStream confResourceAsInputStream = config.getConfResourceAsInputStream("hbase-site.xml");
			int available = 0;
			try {
				available = confResourceAsInputStream.available();
			} catch (Exception e) {
				//for debug purpose
				System.out.println("configuration files not found locally");
			} finally {
				IOUtils.closeQuietly(confResourceAsInputStream);
			}

			System.out.println("available="+available);

			if (available == 0 ) {
				config.addResource("core-site.xml");
				config.addResource("./hbase-site.xml");
				config.addResource("hdfs-site.xml");
			}
			 */

			/*This instantiates an HTable object that connects you to the "test" table*/
			//HConnection connection = HConnectionManager.createConnection(config);

			//HTableInterface table = connection.getTable(TableName.valueOf("aai_props"));
			aaiLogger.info(logline, true, "0");

		} catch (Exception e) {
			aaiLogger.debug(logline, "AAITxnLog: Default Constructor: Exception=" + e.toString());
			aaiLogger.info(logline, false, "AAI_4000");
		}
	}

	/**
	 * Put.
	 *
	 * @param status the status
	 * @param srcId the src id
	 * @param rsrcId the rsrc id
	 * @param rsrcType the rsrc type
	 * @param rqstBuf the rqst buf
	 * @param respBuf the resp buf
	 * @return the string
	 */
	public String put(
			String status,
			String srcId,
			String rsrcId,
			String rsrcType,
			String rqstBuf,
			String respBuf
			) {
		return put ("",status,"","",srcId,rsrcId,rsrcType,rqstBuf,respBuf,false,new NotificationEvent());

	}

	/**
	 * Put.
	 *
	 * @param tid the tid
	 * @param status the status
	 * @param srcId the src id
	 * @param rsrcId the rsrc id
	 * @param rsrcType the rsrc type
	 * @param rqstBuf the rqst buf
	 * @param respBuf the resp buf
	 * @return the string
	 */
	public String put(
			String tid,
			String status,
			String srcId,
			String rsrcId,
			String rsrcType,
			String rqstBuf,
			String respBuf
			) {
		return put (tid,status,"","",srcId,rsrcId,rsrcType,rqstBuf,respBuf,false,new NotificationEvent());
	}

	/**
	 * Put.
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
	 * @return the string
	 */
	public String put(
			String tid,
			String status,
			String rqstTm,
			String respTm,
			String srcId,
			String rsrcId,
			String rsrcType,
			String rqstBuf,
			String respBuf
			) { 
			return put (tid,status,"","",srcId,rsrcId,rsrcType,rqstBuf,respBuf,false,new NotificationEvent());
	}
	
	/**
	 * Put.
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
	public String put(
			String tid,
			String status,
			String rqstTm,
			String respTm,
			String srcId,
			String rsrcId,
			String rsrcType,
			String rqstBuf,
			String respBuf, 
			boolean hasNotificationEvent,
			NotificationEvent ne
			) {
		logline.init(COMPONENT, tid, srcId, "put()");
		//FINEGRAINaaiLogger.debug(logline, "In put: storing hbase config file...");

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
		// String htid = tid + rsrcType;  // orig
		// String htid = tid + srcId;  //final version?
		String htid = tid;// + srcId + "_" + rsrcType;  // use this one for now
		//FINEGRAINaaiLogger.debug(logline, "In put...: htid="+htid);
		//FINEGRAINaaiLogger.debug(logline, "tid: " + tid + ", srcId: " + srcId + " rsrcType: " + rsrcType);

		//need to add a prefix for better hbase logging server balancing
		htid = HbaseSaltPrefixer.getInstance().prependSalt(htid);
		
		if (rqstTm == null || "".equals(rqstTm)) {
			rqstTm = tm;
		}

		if (respTm == null || "".equals(respTm)) {
			respTm = tm;
		}

		try {
			table = new HTable(config, AAIConfig.get(AAIConstants.HBASE_TABLE_NAME));

			Put p = new Put(Bytes.toBytes(htid));

			p.add(Bytes.toBytes("transaction"),Bytes.toBytes("tid"),Bytes.toBytes(tid));
			p.add(Bytes.toBytes("transaction"),Bytes.toBytes("status"),Bytes.toBytes(status));
			p.add(Bytes.toBytes("transaction"),Bytes.toBytes("rqstDate"),Bytes.toBytes(rqstTm));
			p.add(Bytes.toBytes("transaction"),Bytes.toBytes("respDate"),Bytes.toBytes(respTm));
			p.add(Bytes.toBytes("transaction"),Bytes.toBytes("sourceId"),Bytes.toBytes(srcId));

			p.add(Bytes.toBytes("resource"),Bytes.toBytes("resourceId"),Bytes.toBytes(rsrcId));
			p.add(Bytes.toBytes("resource"),Bytes.toBytes("resourceType"),Bytes.toBytes(rsrcType));

			p.add(Bytes.toBytes("payload"),Bytes.toBytes("rqstBuf"),Bytes.toBytes(rqstBuf));
			p.add(Bytes.toBytes("payload"),Bytes.toBytes("respBuf"),Bytes.toBytes(respBuf));

			/* Once you've adorned your Put instance with all the updates you want to
	    	make, to commit it do the following */
			table.put(p);
			table.flushCommits();
			table.close();
			aaiLogger.info(logline, true, "0");
			return htid;
		} catch (Exception e) {
			aaiLogger.debug(logline, "AAITxnLog: put: Exception=", e);
			aaiLogger.info(logline, false, "AAI_4000");
			return htid;
		}
	}

	/**
	 * Gets the.
	 *
	 * @param htid the htid
	 * @return the transaction log entry
	 * @throws AAIException the AAI exception
	 */
	public TransactionLogEntry get(String htid) throws AAIException {

		logline.init(COMPONENT, htid, "aaiInternal", "get()");
		aaiLogger.debug(logline, "In get: searching hbase config file...");
		String tidStr = "";
		TransactionLogEntry txObj = new TransactionLogEntry();

		try {
			table = new HTable(config, AAIConfig.get(AAIConstants.HBASE_TABLE_NAME));

			Get g = new Get(Bytes.toBytes(htid));

			Result r = table.get(g);
			byte [] tid = r.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("tid"));
			byte [] status = r.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("status"));
			byte [] rqstDate = r.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("rqstDate"));
			byte [] respDate = r.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("respDate"));
			byte [] sourceId = r.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("sourceId"));

			byte [] resourceId = r.getValue(Bytes.toBytes("resource"),Bytes.toBytes("resourceId"));
			byte [] resourceType = r.getValue(Bytes.toBytes("resource"),Bytes.toBytes("resourceType"));

			byte [] rqstBuf = r.getValue(Bytes.toBytes("payload"),Bytes.toBytes("rqstBuf"));
			byte [] respBuf = r.getValue(Bytes.toBytes("payload"),Bytes.toBytes("respBuf"));

			byte [] notificationPayload = r.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationPayload"));
			byte [] notificationStatus = r.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationStatus"));
			byte [] notificationId = r.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationId"));
			byte [] notificationTopic = r.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationTopic"));
			byte [] notificationEntityLink = r.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationEntityLink"));
			byte [] notificationAction = r.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationAction"));
			// TODO: if tx logging in on, we're going to duplicate this, do we want to?
//			aaiLogger.debug(logline, "AAITxnLog: got row=" +
//					tidStr + "|" +
//					Bytes.toString(status) + "|" +
//					Bytes.toString(rqstDate) + "|" +
//					Bytes.toString(respDate) + "|" +
//					Bytes.toString(sourceId) + "|" +
//					Bytes.toString(resourceId) + "|" +
//					Bytes.toString(resourceType) + "|" +
//					Bytes.toString(rqstBuf) + "|" +
//					Bytes.toString(respBuf));

			table.close();
			
			tidStr = Bytes.toString(tid);
			txObj.setTransactionLogEntryId(tidStr);
			txObj.setStatus(Bytes.toString(status));
			txObj.setRqstDate(Bytes.toString(rqstDate));
			txObj.setRespDate(Bytes.toString(respDate));
			txObj.setSourceId(Bytes.toString(sourceId));
			txObj.setResourceId(Bytes.toString(resourceId));
			txObj.setResourceType(Bytes.toString(resourceType));
			txObj.setRqstBuf(Bytes.toString(rqstBuf));
			txObj.setrespBuf(Bytes.toString(respBuf));
			txObj.setNotificationPayload(Bytes.toString(notificationPayload));
			txObj.setNotificationStatus(Bytes.toString(notificationStatus));
			txObj.setNotificationId(Bytes.toString(notificationId));
			txObj.setNotificationTopic(Bytes.toString(notificationTopic));
			txObj.setNotificationEntityLink(Bytes.toString(notificationEntityLink));
			txObj.setNotificationAction(Bytes.toString(notificationAction));
		} catch (IOException e) {
			aaiLogger.debug(logline,  "IOException on hbase call", e);
			throw new AAIException("AAI_4000");
		}
		
		return txObj;
	}

	
	/**
	 * Scan filtered.
	 *
	 * @param startMillis the start millis
	 * @param endMillis the end millis
	 * @param methodList the method list
	 * @param putFilter the put filter
	 * @param getFilter the get filter
	 * @param resourceFilter the resource filter
	 * @param fromAppIdFilter the from app id filter
	 * @return the transaction log entries
	 */
	public TransactionLogEntries scanFiltered(long startMillis, long endMillis, List<String> methodList, 
			String putFilter, String getFilter, String resourceFilter, String fromAppIdFilter) {

		logline.init(COMPONENT, "scanFiltered", "aaiInternal", "scanFiltered()");
		aaiLogger.debug(logline, "Starting scanFiltered()");
		
		//		we should have the config ready from the constructor
		
		TransactionLogEntries txs = new TransactionLogEntries();
			
		if (config == null) {
			aaiLogger.debug(logline, "in scan: can't create HBase configuration");
			return txs;
		}
		
		try {
			table = new HTable(config, AAIConfig.get(AAIConstants.HBASE_TABLE_NAME));
			//Scan s = new Scan();
			//s.addColumn(Bytes.toBytes("transaction"), Bytes.toBytes("tid"));
			//s.addColumn(Bytes.toBytes("payload"), Bytes.toBytes("rqstBuf"));
			Scan s = new Scan();
			FilterList flMaster = new FilterList(FilterList.Operator.MUST_PASS_ALL);
			FilterList methodflMaster = new FilterList(FilterList.Operator.MUST_PASS_ONE);
			if (methodList != null) { 
				for (String method : methodList) { 
					Filter filt = new SingleColumnValueFilter(Bytes.toBytes("resource"),
							Bytes.toBytes("resourceType"), CompareOp.EQUAL, Bytes.toBytes(method));
					methodflMaster.addFilter(filt);
				}
				flMaster.addFilter(methodflMaster);
			}
			
			if (getFilter != null) { 
				Filter filt = new SingleColumnValueFilter(Bytes.toBytes("payload"),
						Bytes.toBytes("respBuf"), CompareOp.EQUAL, new RegexStringComparator(getFilter));
				flMaster.addFilter(filt);
			}
			if (putFilter != null) { 
				Filter filt = new SingleColumnValueFilter(Bytes.toBytes("payload"),
						Bytes.toBytes("rqstBuf"), CompareOp.EQUAL, new RegexStringComparator(putFilter));
				flMaster.addFilter(filt);
			}
			if (resourceFilter != null) { 
				Filter filt = new SingleColumnValueFilter(Bytes.toBytes("resource"),
						Bytes.toBytes("resourceId"), CompareOp.EQUAL, new RegexStringComparator(resourceFilter));
				flMaster.addFilter(filt);
			}
			if (fromAppIdFilter != null) { 
				Filter filt = new SingleColumnValueFilter(Bytes.toBytes("transaction"),
						Bytes.toBytes("sourceId"), CompareOp.EQUAL, new RegexStringComparator("^" + fromAppIdFilter));
				flMaster.addFilter(filt);
			}
			
			if (flMaster.hasFilterRow()) { 
				s.setFilter(flMaster);
			}
			
			s.setTimeRange(startMillis, endMillis);
			ResultScanner scanner = table.getScanner(s);

			try {		
				for (Result rr = scanner.next(); rr != null; rr = scanner.next()) {
					
					byte [] tid = rr.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("tid"));
					byte [] status = rr.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("status"));
					byte [] rqstDate = rr.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("rqstDate"));
					byte [] respDate = rr.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("respDate"));
					byte [] sourceId = rr.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("sourceId"));

					byte [] resourceId = rr.getValue(Bytes.toBytes("resource"),Bytes.toBytes("resourceId"));
					byte [] resourceType = rr.getValue(Bytes.toBytes("resource"),Bytes.toBytes("resourceType"));

					byte [] rqstBuf = rr.getValue(Bytes.toBytes("payload"),Bytes.toBytes("rqstBuf"));
					byte [] respBuf = rr.getValue(Bytes.toBytes("payload"),Bytes.toBytes("respBuf"));
					
					byte [] notificationPayload = rr.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationPayload"));
					byte [] notificationStatus = rr.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationStatus"));
					byte [] notificationId = rr.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationId"));
					byte [] notificationTopic = rr.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationTopic"));
					byte [] notificationEntityLink = rr.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationEntityLink"));
					byte [] notificationAction = rr.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationAction"));
					TransactionLogEntry txObj = new TransactionLogEntry();
					String tidStr = Bytes.toString(tid);
					txObj.setTransactionLogEntryId(tidStr);
					txObj.setStatus(Bytes.toString(status));
					txObj.setRqstDate(Bytes.toString(rqstDate));
					txObj.setRespDate(Bytes.toString(respDate));
					txObj.setSourceId(Bytes.toString(sourceId));
					txObj.setResourceId(Bytes.toString(resourceId));
					txObj.setResourceType(Bytes.toString(resourceType));
					txObj.setRqstBuf(Bytes.toString(rqstBuf));
					txObj.setrespBuf(Bytes.toString(respBuf));
					txObj.setNotificationPayload(Bytes.toString(notificationPayload));
					txObj.setNotificationStatus(Bytes.toString(notificationStatus));
					txObj.setNotificationId(Bytes.toString(notificationId));
					txObj.setNotificationTopic(Bytes.toString(notificationTopic));
					txObj.setNotificationEntityLink(Bytes.toString(notificationEntityLink));
					txObj.setNotificationAction(Bytes.toString(notificationAction));
					txs.getTransactionLogEntries().add(txObj);
				}
			} finally {
				// Make sure you close your scanners when you are done!
				scanner.close();
			}
			table.close();
		} catch (Exception e) {
			aaiLogger.debug(logline, "AAITxnLog: scan: Exception=" + e.toString());
		}
		
		return txs;
	}

	/**
	 * Scan.
	 *
	 * @param htid the htid
	 * @return the list
	 */
	public List<String> scan(String htid) {

		List<String> list = new ArrayList<String>();
		logline.init(COMPONENT, htid, "aaiInternal", "scan()");
		aaiLogger.debug(logline, "In scan: searching hbase config file...");
		//		we should have the config ready from the constructor
		if (config == null) {
			aaiLogger.debug(logline, "in scan: can't create HBase configuration");
			return list;
		}

		try {
			table = new HTable(config, AAIConfig.get(AAIConstants.HBASE_TABLE_NAME));
			//Scan s = new Scan();
			//s.addColumn(Bytes.toBytes("transaction"), Bytes.toBytes("tid"));
			//s.addColumn(Bytes.toBytes("payload"), Bytes.toBytes("rqstBuf"));
			Scan s = new Scan(Bytes.toBytes(htid));
			ResultScanner scanner = table.getScanner(s);

			try {		
				for (Result rr = scanner.next(); rr != null; rr = scanner.next()) {
					list.add(rr.toString());
					aaiLogger.debug(logline, "in scan: Found row : " + rr);

				}
			} finally {
				// Make sure you close your scanners when you are done!
				scanner.close();
			}
			table.close();
		} catch (Exception e) {

			aaiLogger.debug(logline, "AAITxnLog: scan: Exception=" + e.toString());
		}
		return list;
	}

}

/*
Need to implement HBase Connection Pooling in the future.
This is to reduce the 1 second delay during the first open of HConnection, and HTable instantiation. 
Hbase provides the Hconnection class and the HConnectionManager class. 
Both provifde the functionaltity similar to jdbc connection pooling 
to share pre-existing opened connections.
Here we should be able to use the getTable() method to get a 
reference to an HTable instance. 

 */
