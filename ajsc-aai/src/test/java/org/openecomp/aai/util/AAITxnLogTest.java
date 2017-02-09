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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.openecomp.aai.domain.notificationEvent.NotificationEvent;
import org.openecomp.aai.domain.translog.TransactionLogEntries;
import org.openecomp.aai.domain.translog.TransactionLogEntry;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAITxnLog;
import org.openecomp.aai.util.PojoUtils;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.rule.PowerMockRule;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;


@PrepareForTest({HBaseConfiguration.class, Configuration.class, 
	HTable.class, Result.class, ResultScanner.class, Scan.class, 
	Get.class, NotificationEvent.class, 
	NotificationEvent.EventHeader.class, PojoUtils.class, AAITxnLog.class}) 

public class AAITxnLogTest {
	
	@Rule
	public PowerMockRule rule = new PowerMockRule();
	
	static {
	     PowerMockAgent.initializeIfNeeded();
	 }
	
	AAITxnLog aaiTnxLog;
	Configuration config;
	HTable htable;
	Result result;
	ResultScanner resScanner;
	Scan scan;
	Get g;
	NotificationEvent notif;
	NotificationEvent.EventHeader ehNotif;
	PojoUtils pu;

	boolean hasNotifEvent = true;
	final String notifPayload = "A random payload";
	final String notifID = "1";
	final String notifEntityLink = "nLink";
	final String notifAction = "nAction";
	final String notifStatus = "nStatus";
	final String notifTopic = "nTopic";
	
	final String tid = "tidVal";
	final String status = "statusVal";
	final String rqstTm = "rqstTmVal";
	final String respTm = "respTmVal";
	final String srcId = "srcIdVal";
	final String rsrcId = "rsrcIdVal";
	final String rsrcType = "rsrcTypeVal";
	final String rqstBuf = "rqstBufVal";
	final String respBuf = "respBufVal";
	
	
	/**
	 * Initialize.
	 */
	@Before
	public void initialize(){
		partialSetupForAAIConfig();
		PowerMockito.mockStatic(HBaseConfiguration.class);
		config = PowerMockito.mock(Configuration.class);
		htable = PowerMockito.mock(HTable.class);
		result = PowerMockito.mock(Result.class);
		resScanner = PowerMockito.mock(ResultScanner.class);
		scan = PowerMockito.mock(Scan.class);
		g = PowerMockito.mock(Get.class);
		notif = PowerMockito.mock(NotificationEvent.class);
		ehNotif = PowerMockito.mock(NotificationEvent.EventHeader.class);
		pu = PowerMockito.mock(PojoUtils.class); 
	
		
		mockNotificationEvent();
		
		Mockito.when(HBaseConfiguration.create()).thenReturn(config);
		aaiTnxLog = new AAITxnLog(tid, srcId);

		try {
			PowerMockito.whenNew(HTable.class).withAnyArguments().thenReturn(htable);
			PowerMockito.whenNew(Get.class).withAnyArguments().thenReturn(g);
			PowerMockito.whenNew(PojoUtils.class).withAnyArguments().thenReturn(pu);
			PowerMockito.whenNew(Scan.class).withAnyArguments().thenReturn(scan);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		mockResult();
		
		try {
			PowerMockito.when(htable.get(g)).thenReturn(result);
			PowerMockito.when(htable.getScanner(scan)).thenReturn(resScanner);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Method to test 'put' operation without a notification event.
	 */
	@Test
	public void testPut_withoutNotifEvent(){
		String htid = aaiTnxLog.put(tid, status, rqstTm, respTm, srcId, rsrcId, rsrcType, rqstBuf, respBuf);
		try {
			TransactionLogEntry tle = aaiTnxLog.get(htid);
			hasNotifEvent = false;
			validateTransactionLogEntry(tle);
		} catch (AAIException e) {
			fail("Cant read back data from htable");
			e.printStackTrace();
		}
	}
	
	/**
	 * Method to test 'put' operation with a notification event.
	 */
	@Test
	public void testPut_withNotifEvent(){
		hasNotifEvent = true;
		String htid = aaiTnxLog.put(tid, status, rqstTm, respTm, srcId, rsrcId, rsrcType, rqstBuf, respBuf, hasNotifEvent, notif);
		try {
			TransactionLogEntry tle = aaiTnxLog.get(htid);
			validateTransactionLogEntry(tle);
		} catch (AAIException e) {
			fail("Cant read back data from htable");
			e.printStackTrace();
		}
	}
	
	/**
	 * Method to test 'scan' over an empty htable.
	 */
	@Test
	public void testScan_withEmptyHTable(){
		String key = tid;
		List<String> res = aaiTnxLog.scan(key);
		assertTrue("Scan output should be empty", res.size() == 0 );
	}
	
	/**
	 * Method to test 'scan' operation.
	 */
	@Test
	public void testScan(){
		try {
			PowerMockito.when(resScanner.next()).thenReturn(result).thenReturn(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
		List<String> res = aaiTnxLog.scan(tid);
		assertTrue("Scan output should not be empty", res.size()==1);
		assertTrue("Did not find entry in 'scan'", res.get(0).equals(result.toString()));
	}
	
	/**
	 * Method to test 'scanFiltered' with an empty htable.
	 */
	@Test 
	public void testScanFiltered_withEmptyHTable(){
		aaiTnxLog.put(tid, status, rqstTm, respTm, srcId, rsrcId, rsrcType, rqstBuf, respBuf, true, new NotificationEvent());
		TransactionLogEntries tles = aaiTnxLog.scanFiltered(0, 100, null, null, null, null, null);
		assertTrue ("scanFilstered output should be empty", tles.getTransactionLogEntries().size() == 0);
	}
	
	/**
	 * Method to test 'scanFiltered' operation.
	 */
	@Test 
	public void testScanFiltered(){
		try {
			PowerMockito.when(resScanner.next()).thenReturn(result).thenReturn(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
		aaiTnxLog.put(tid, status, rqstTm, respTm, srcId, rsrcId, rsrcType, rqstBuf, respBuf, true, new NotificationEvent());
		TransactionLogEntries tles = aaiTnxLog.scanFiltered(0, 100, null, null, null, null, null);
		assertFalse ("scanFilstered output should not be empty", tles.getTransactionLogEntries().size() == 0);
		validateTransactionLogEntry(tles.getTransactionLogEntries().get(0));
	}
	
	/**
	 * Helper method to validate the contents of a TransactionalLogEntry.
	 *
	 * @param tle TransactionalLogEntry to compare against
	 */
	public void validateTransactionLogEntry(TransactionLogEntry tle){
		String pre = "validateTransactionLogEntry: ";
		String post = " didn't match";
		assertEquals(pre + "tid" + post, tle.getTransactionLogEntryId(), tid);
		assertEquals(pre + "status" + post, tle.getStatus(), status);
		assertEquals(pre + "rqstDate" + post, tle.getRqstDate(), rqstTm);
		assertEquals(pre + "respDate" + post, tle.getRespDate(), respTm);
		assertEquals(pre + "srcId" + post, tle.getSourceId(), srcId);
		assertEquals(pre + "rsrcId" + post, tle.getResourceId(), rsrcId);
		assertEquals(pre + "rqstBuf" + post, tle.getRqstBuf(), rqstBuf);
		assertEquals(pre + "respBuf" + post, tle.getrespBuf(), respBuf);
		if ( hasNotifEvent){
			assertEquals(pre + "notifPayload" + post, tle.getNotificationPayload(), notifPayload);
			assertEquals(pre + "notifStatus" + post, tle.getNotificationStatus(), notifStatus);
			assertEquals(pre + "notifID" + post, tle.getNotificationId(), notifID);
			assertEquals(pre + "notifTopic" + post, tle.getNotificationTopic(), notifTopic);
			assertEquals(pre + "notifEntityLink" + post, tle.getNotificationEntityLink(), notifEntityLink);
			assertEquals(pre + "notifAction" + post, tle.getNotificationAction(), notifAction);
		}
	}
	
	
	
	/**
	 * Helper method to mock PojoUtils.
	 */
	public void mockPojoUtils(){
		
		try {
			PowerMockito.when(pu.getJsonFromObject(notif)).thenReturn(notifPayload);
		} catch (JsonGenerationException e) {e.printStackTrace();} 
		  catch (JsonMappingException e) {e.printStackTrace();}
		  catch (IOException e) {e.printStackTrace();	}
	}
	

	
	/**
	 * Helper method to mock a notification event handler.
	 */
	public void mockNotifEventHandler(){
		PowerMockito.when(ehNotif.getId()).thenReturn(notifID);
		PowerMockito.when(ehNotif.getEntityLink()).thenReturn(notifEntityLink);
		PowerMockito.when(ehNotif.getAction()).thenReturn(notifAction);
		PowerMockito.when(ehNotif.getStatus()).thenReturn(notifStatus);
	}
	
	/**
	 * Helper method to mock a notification event.
	 */
	public void mockNotificationEvent(){
		mockPojoUtils();
		mockNotifEventHandler();
		PowerMockito.when(notif.getEventHeader()).thenReturn(ehNotif);
		PowerMockito.when(notif.getEventHeader().getEventType()).thenReturn(null);
		PowerMockito.when(notif.getEventHeader().getStatus()).thenReturn(null);
	}
	
	
	/**
	 * Helper method to build a mock-Result.
	 */
	public void mockResult(){
		PowerMockito.when(result.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("tid"))).thenReturn(Bytes.toBytes(tid));
		PowerMockito.when(result.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("status"))).thenReturn(Bytes.toBytes(status));
		PowerMockito.when(result.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("rqstDate"))).thenReturn(Bytes.toBytes(rqstTm));
		PowerMockito.when(result.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("respDate"))).thenReturn(Bytes.toBytes(respTm));
		PowerMockito.when(result.getValue(Bytes.toBytes("transaction"),Bytes.toBytes("sourceId"))).thenReturn(Bytes.toBytes(srcId));
		
		PowerMockito.when(result.getValue(Bytes.toBytes("resource"),Bytes.toBytes("resourceId"))).thenReturn(Bytes.toBytes(rsrcId));
		PowerMockito.when(result.getValue(Bytes.toBytes("resource"),Bytes.toBytes("resourceType"))).thenReturn(Bytes.toBytes(rsrcType));
		
		PowerMockito.when(result.getValue(Bytes.toBytes("payload"),Bytes.toBytes("rqstBuf"))).thenReturn(Bytes.toBytes(rqstBuf));
		PowerMockito.when(result.getValue(Bytes.toBytes("payload"),Bytes.toBytes("respBuf"))).thenReturn(Bytes.toBytes(respBuf));
		
		PowerMockito.when(result.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationPayload"))).thenReturn(Bytes.toBytes(notifPayload));
		PowerMockito.when(result.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationStatus"))).thenReturn(Bytes.toBytes(notifStatus));
		PowerMockito.when(result.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationId"))).thenReturn(Bytes.toBytes(notifID));
		PowerMockito.when(result.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationTopic"))).thenReturn(Bytes.toBytes(notifTopic));
		PowerMockito.when(result.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationEntityLink"))).thenReturn(Bytes.toBytes(notifEntityLink));
		PowerMockito.when(result.getValue(Bytes.toBytes("notification"),Bytes.toBytes("notificationAction"))).thenReturn(Bytes.toBytes(notifAction));
	}
	
    
	/**
	 * Helper method to load aai config from test configuration file
	 * This requires that the 'test_aaiconfig.properties' file is available 
	 */

	static void setFinalStatic(Field field, Object newValue) throws Exception {
	  field.setAccessible(true);
	  Field modifiersField = Field.class.getDeclaredField("modifiers");
	  modifiersField.setAccessible(true);
	  modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
	  field.set(null, newValue);
	}
	
	/**
	 * Partial setup for AAI config.
	 */
	public void partialSetupForAAIConfig(){
		
	  try {
		  setFinalStatic(AAIConfig.class.getDeclaredField("GlobalPropFileName"),  "src/test/resources/test_aaiconfig.properties");
	  } 
	  catch (SecurityException e) {fail();}
	  catch (NoSuchFieldException e) {fail();}
	  catch (Exception e) {fail();}
	  
	  AAIConfig.reloadConfig();
	}
	
    /**
     * Helper method to set the file name of aaiconfig.properties file
     *
     * @param field Private static filed for update
     * @param newValue New value to be used
     * @throws Exception the exception
     */
    public void modifyFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);        
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

}
