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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.MDC;
/**
 * This class is used to help standardize how log lines are written and provide profiling info.
 *
 * Note that the name-value pairs are assembled in the order they are inserted.. Init sets up the tr, fr and co values 
 * so that they can be used consistently via start() for debug and error logs. finish() adds the ss and tt so they
 * can be provided for each INFO record.
 */

public class LogLine {

	private long startTime = 0;
	private long endTime = 0;
	
	private String co = ""; // component
	private String tr = ""; // transId
	private String ll = ""; // log level
	
	private String fr = ""; // fromAppId
	private String to = ""; // toAppId
	private String me = ""; // operation
	
	private String tt = ""; // time taken
	private String ss = ""; // success
	private String ec = "0"; // error code
	private String et = ""; // error text 
	
	
	private String userContributed = "";
	
	/**
	 *  Initialize the start time and identify the component, transactionId and fromAppId
	 * which are mandatory to log on each line.
	 *
	 * @param component identifies the subsystem or component of the application
	 * @param transId identifies the unique transaction id for the request being processed
	 * @param fromAppId identifies the application that is making the request
	 * @param operation the operation
	 */
	

	
	public void init(String component, String transId, String fromAppId, String operation){

		init(component, transId, fromAppId, "AAI", operation);
	}
	
	/**
	 * Inits the logline.
	 *
	 * @param component the component
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param toAppId the to app id
	 * @param operation the operation
	 */
	public void init(String component, String transId, String fromAppId, String toAppId, String operation){

		this.setUserContributed("");
		startTime = System.currentTimeMillis();
		this.setCo(component);
		this.setTr(transId);
		this.setFr(fromAppId);
		this.setTo(toAppId);
		this.setMe(operation);
	}
		
	/**
	 *  Overrides the value from init().
	 */
	public void startTimer() {
		startTime = System.currentTimeMillis();
	}
	
	/**
	 * Adds the.
	 *
	 * @param name the name
	 * @param value the value
	 */
	public void add(String name, String value) {
		String uc = getUserContributed();
		if (value != null) {
			uc += ":" + name + "=" + value.replaceAll("\\|", "^").trim();
		} else { 
			uc += ":" + name + "=" + value;
		}
		setUserContributed(uc);
	}

	/**
	 * Adds the.
	 *
	 * @param name the name
	 * @param value the value
	 */
	public void add(String name, int value) {
		String uc = getUserContributed();
		uc += ":" + name + "=" +  Integer.toString(value);
		setUserContributed(uc);
	}

	/**
	 * Adds the.
	 *
	 * @param name the name
	 * @param value the value
	 */
	public void add(String name, long value) {
		String uc = getUserContributed();
		uc += ":" + name + "=" +  Long.toString(value);
		setUserContributed(uc);
	}
	
	/**
	 * Adds the.
	 *
	 * @param name the name
	 * @param value the value
	 */
	public void add(String name, double value) {
		String uc = getUserContributed();
		uc += ":" + name + "=" +  Double.toString(value);
		setUserContributed(uc);
	}

	/**
	 * Adds the.
	 *
	 * @param name the name
	 * @param value the value
	 */
	public void add(String name, boolean value) {
		String uc = getUserContributed();
		uc += ":" + name + "=" +  Boolean.toString(value);
		setUserContributed(uc);
	}
	
	/**
	 *  Return the log line based on what we have so far.
	 *
	 * @param audit the audit
	 * @return the line
	 */
	protected String getLine(boolean audit) {
	
	// MDC is setup when AAILogger is setup
	String hostName = (String)MDC.get("hostname")== null ? "" : (String)MDC.get("hostname");
	String hostAddress = (String)MDC.get("hostaddress") == null ? "" :  (String)MDC.get("hostaddress");
	//String className = (String)MDC.get("classname") == null ? "" :  (String)MDC.get("classname");
	String className = "";
	
	Exception ex = new Exception();
	StackTraceElement[] stackTraceArray = ex.getStackTrace();
	
	//Find the class in the stack trace that calls the AAILogger.
	for (int i =0; i < stackTraceArray.length; i++ ){
		String fullClassName = stackTraceArray[i].getClassName();
		if (fullClassName.contains("AAILogger")){
			int aaiLoggerIndex = i;
			String nextClassInStackTrace = stackTraceArray[i+1].getClassName();
			if (nextClassInStackTrace.contains("AAILogger")){
				fullClassName = stackTraceArray[i+2].getClassName();
			} else {
				fullClassName = nextClassInStackTrace;
			}
			className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1 );
			break;
		}
	}
	
//	if (ex.getStackTrace().length > 3) { // 3rd in stack shd be aaiLogger so we need the 4th
//			String fullClassName = ex.getStackTrace()[3].getClassName();
//			if (!fullClassName.contains("aai"))
//				fullClassName = ex.getStackTrace()[2].getClassName();
//			className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1 );
//	}	
	
		// this will format it 
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	
	Date startDate = new Date(startTime);
	String startTimeStr = dateFormat.format(startDate);	
	
	Date endDate = new Date(endTime);
	String endTimeStr = dateFormat.format(endDate);	
	
	String timeTaken = this.getTt();
	
	if (!ll.equals("INFO")) //if this is debug or error, use the now time
	{
		// "endtime" will now be equal to when the log is written (nowtime )	
		long nowTime = System.currentTimeMillis();
		timeTaken = String.valueOf(nowTime - startTime); //tt is "time taken" or "elapsed time"
		Date nowDate = new Date (nowTime);
		endTimeStr = dateFormat.format(nowDate);	
	
	}
							
		
	return    startTimeStr  				 				+ // start timestamp 
				"|"  + endTimeStr  			 				+ // end timestamp
				"|"  + this.getTr()          				+ // RequestId              					=> transId
				"|"                          				+ // serviceInstanceId      					=> NA
				"|"                          				+ // threadId               					=> NA 
				"|"                          				+ // physical server name   					=> NA
				"|" + this.getMe()           				+ // serviceName method       					=> operation
				"|" + this.getFr()            	            + // partnerName from                           => fromAppId  
				(audit ? "" :"|" + this.getTo())    	    + // TargetEntity                               => toAppID
				(audit ? "" :"|")           				+ //TargetServiceName							=> NA 
				"|"	+ this.getSs()					 		+ //StatusCode/success							=> NA 
				"|"	+ this.getEc()					 		+ //Response Code, is our error code			=> NA 
				"|"	+ this.getEt()						 	+ // Response Description, is our error text 	=> NA 
				"|"                          				+ // instanceUUID          					=> NA
				"|" + this.getLevel()        				+ // category               					=> loglevel 
				"|"                          				+ // severity               					=> NA
				"|" + hostAddress	         				+ // Server IP address      					=> hostAddress 
				"|" + timeTaken           	 				+ // Timer                  					=> tt
				"|" + hostName				 				+ // Server                 					=> hostName 
				"|" 					     				+ // IP Address 								=> NA 
				"|" + className			     				+ // className              					=> className
				"|"						 	 				+ //Unused 									=> NA 
				"|"							 				+ //ProcessKey									=> NA 
				(audit ? "": "|")			 				+ //TargetVirtualEntity 						=> NA 
				"|"							 				+ //CustomField1 								=> NA 
				"|"							 				+ //CustomField2 								=> NA 
				"|"							 				+ //CustomField3 								=> NA 
				"|"							 				+ //CustomField4 								=> NA 
				"|co=" + this.getCo()        				+ // DetailMessage component                    => component		
				    ":" + this.getUserContributed() 		+ "|";
	

	}
			
	
	/**
	 *  Return the finished log line, including success and elapsed time. 
	 * This should be called at the end for INFO logs
	 *
	 * @param success the success
	 * @return the string
	 */
	public String finish(boolean success) {
		endTime = System.currentTimeMillis();
		setSs((success ? "COMPLETE" : "ERROR"));
		setTt(String.valueOf(endTime - startTime)); // tt is "time taken" or "elapsed time" 
		return getLine(false);
	}

	/**
	 * Gets the co.
	 *
	 * @return the co
	 */
	private String getCo() {
		return co;
	}

	/**
	 * Sets the co.
	 *
	 * @param co the new co
	 */
	private void setCo(String co) {
		this.co = co;
	}

	/**
	 * Gets the tr.
	 *
	 * @return the tr
	 */
	private String getTr() {
		return tr;
	}

	/**
	 * Sets the tr.
	 *
	 * @param tr the new tr
	 */
	private void setTr(String tr) {
		this.tr = tr;
	}
	
	/**
	 * Gets the level.
	 *
	 * @return the level
	 */
	private String getLevel() {
		return ll;
	}

	/**
	 * Sets the level.
	 *
	 * @param ll the new level
	 */
	public void setLevel(String ll) {
		this.ll = ll;
	}

	/**
	 * Gets the fr.
	 *
	 * @return the fr
	 */
	private String getFr() {
		return fr;
	}

	/**
	 * Sets the fr.
	 *
	 * @param fr the new fr
	 */
	private void setFr(String fr) {
		this.fr = fr;
	}
	
	/**
	 * Gets the to.
	 *
	 * @return the to
	 */
	private String getTo() {
		return to;
	}

	/**
	 * Sets the to.
	 *
	 * @param to the new to
	 */
	private void setTo(String to) {
		this.to = to;
	}
	
	/**
	 * Gets the me.
	 *
	 * @return the me
	 */
	private String getMe() {
		return me;
	}

	/**
	 * Sets the me.
	 *
	 * @param me the new me
	 */
	private void setMe(String me) {
		this.me = me;
	}

	/**
	 * Gets the tt.
	 *
	 * @return the tt
	 */
	private String getTt() {
		return tt;
	}

	/**
	 * Sets the tt.
	 *
	 * @param tt the new tt
	 */
	private void setTt(String tt) {
		this.tt = tt;
	}

	/**
	 * Gets the ss.
	 *
	 * @return the ss
	 */
	private String getSs() {
		return ss;
	}

	/**
	 * Sets the ss.
	 *
	 * @param ss the new ss
	 */
	protected void setSs(String ss) {
		this.ss = ss;
	}
	
	/**
	 * Sets the ss.
	 *
	 * @param ss the new ss
	 */
	public void setSs(Boolean ss) {
		if (ss)
			this.ss = "y";
		else
			this.ss = "n";
	}
	
	/**
	 * Gets the ec.
	 *
	 * @return the ec
	 */
	public String getEc() {
		return ec;
	}

	/**
	 * Sets the ec.
	 *
	 * @param ec the new ec
	 */
	public void setEc(String ec) {
		this.ec = ec;
	}
	
	/**
	 * Gets the et.
	 *
	 * @return the et
	 */
	public String getEt() {
		return et;
	}

	/**
	 * Sets the et.
	 *
	 * @param et the new et
	 */
	public void setEt(String et) {
		this.et = et;
	}


	/**
	 * Gets the user contributed.
	 *
	 * @return the user contributed
	 */
	private String getUserContributed() {
		return userContributed;
	}

	/**
	 * Sets the user contributed.
	 *
	 * @param userContributed the new user contributed
	 */
	protected void setUserContributed(String userContributed) {
		this.userContributed = userContributed;
	}
		
}
