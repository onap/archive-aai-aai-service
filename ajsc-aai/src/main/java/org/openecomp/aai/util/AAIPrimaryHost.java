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


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;

import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;

import com.att.eelf.configuration.EELFLogger;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;


/**
 * This class provides Logger methods for the AAI application 
 */

public class AAIPrimaryHost {
	
		protected static AAILogger aaiLogger = new AAILogger(AAIPrimaryHost.class.getName());
		protected LogLine logline = new LogLine();
		public EELFLogger logger;
		
		private final String isReachable = new String("isReachable");
		private final String binPing = new String("binPing");
		private final String netcat = new String("netcat");
		private final String echo = new String("echo");
		private final String DEFAULT_CHECK = new String("aai.primary.filetransfer.");
		private String transId = UUID.randomUUID().toString();
		private String fromAppId = "AAI-INIT";
		

		/**
		 * Instantiates a new AAI primary host.
		 *
		 * @param transId the trans id
		 * @param fromAppId the from app id
		 */
		public AAIPrimaryHost(String transId, String fromAppId) {
			this.fromAppId = fromAppId;
			this.transId = transId;
		}

		/**
		 * Do command.
		 *
		 * @param command the command
		 * @return the int
		 * @throws Exception the exception
		 */
		public int doCommand(List<String> command)  
				  throws Exception 
		{ 
			String s = null; 
				      
			ProcessBuilder pb = new ProcessBuilder(command); 
			Process process = pb.start(); 
				  
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream())); 
			BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream())); 
				  
				    // read the output from the command 
			//System.out.println("Here is the standard output of the command:\n"); 
			while ((s = stdInput.readLine()) != null) 
			{ 
				      //System.out.println(s); 
				aaiLogger.debug(logline, "stdout "+ s );
			} 
				  
				    // read any errors from the attempted command 
			System.out.println("Here is the standard error of the command (if any):\n"); 
			while ((s = stdError.readLine()) != null) 
			{ 
				      //System.out.println(s); 
				aaiLogger.debug(logline, "stderrbbbbbb "+ s );
			} 
			return process.waitFor();
		} 
				    


		/**
		 * Method amIPrimary.
		 * @return boolean
		 */
		
		public boolean amIPrimary() 
		{
			return amIPrimary( DEFAULT_CHECK );
		}
		
		/**
		 * Am I primary using echo.
		 *
		 * @param hostname the hostname
		 * @param aaiPrimaryCheck the aai primary check
		 * @param aaiServerList the aai server list
		 * @return true, if successful
		 */
		public boolean amIPrimaryUsingEcho( String hostname, String aaiPrimaryCheck, String aaiServerList) {
			
			String methodName = "amIPrimaryUsingEcho";
			
			logline.init("aaigen", transId, fromAppId, methodName);
			
			StringTokenizer st = new StringTokenizer( aaiServerList, "|" );
        	String host;
        	String msg = null;
        	while ( st.hasMoreTokens() ) {
        		host = st.nextToken();
        				
				String portWithEndpoint = aaiPrimaryCheck.substring(aaiPrimaryCheck.indexOf(":"));
				//System.out.println( "using portEndpoint: " + portWithEndpoint);
				Client client = null;
				
				try {
					client = HttpsAuthClient.getTwoWaySSLClient();
				}
				catch (KeyManagementException kme){
					msg = "KeyManagementException in REST call to echo: " + kme.toString();
				} catch (Exception e) {
					msg = " Exception in REST call to echo: " + e.toString();
				}
				
				if ( msg != null ) {
		    		logline.add( "problem using echo " + msg + " no primary found ",  aaiServerList );
		    		aaiLogger.info(logline, false, "AAI_7401");
					return false;
				}
				String resource = "https://" + host + portWithEndpoint;
				WebResource webResource = client
				   .resource(resource);
			

				
				try {
					ClientResponse response = webResource.accept("application/json")
					         .header("X-TransactionId", transId)
					         .header("X-FromAppId",  "PrimaryCheck")
			                   .get(ClientResponse.class);
			
					String output = response.getEntity(String.class);
				
					if (response.getStatus() != 200) {
						aaiLogger.debug(logline, "echo status " + response.getStatus() + " echo response  "+ output );
						logline.add( "unexpected status ", response.getStatus() );
						aaiLogger.info(logline, false, "AAI_7402");
					} else {
						aaiLogger.debug(logline, "echo response from server  "+ output );
						if ( host.contains(hostname)) {
							return true;
						}
						return false;
					}
				} catch ( Exception e) {
					logline.add( "exception checking primary ", resource );
					aaiLogger.info(logline, false, "AAI_4000", e);
				}
			}
    		logline.add( "no primary found ",  aaiServerList );
    		aaiLogger.info(logline, false, "AAI_4000");
			return false;
		}
		
		/**
		 * Am I primary.
		 *
		 * @param checkName the check name
		 * @return true, if successful
		 */
		public boolean amIPrimary(String checkName) 
		{
			String methodName = "amIPrimary";
			
			logline.init("aaigen", transId, fromAppId, methodName);
			String aaiServerList = null;
			String aaiPrimaryCheck = null;
			String aaiPingTimeout = null;
			String aaiPingCount = null;
			
			int timeout = -1;
			
			String msg = null;
			
			try {
				
				aaiServerList = AAIConfig.get(checkName + "serverlist");
				//aaiPrimaryCheck = isReachable;
				aaiPrimaryCheck = AAIConfig.get(checkName + "primarycheck");
				//String aaiPingTimeout = "5000";
				aaiPingTimeout = AAIConfig.get(checkName + "pingtimeout");
				timeout = (new Integer(aaiPingTimeout)).intValue();
				//aaiPingCount = "5";
				aaiPingCount = AAIConfig.get(checkName + "pingcount");
			} catch ( Exception e ) {
				msg = "missing aaiconfig.properties for primary check, no check is done";
        		logline.add( "unable to check primary ", msg );
        		aaiLogger.info(logline, true, "0");
        		return true;
			}
			
			aaiLogger.debug(logline, "checking primary  "+ aaiPrimaryCheck + " on " + aaiServerList + " timeout " + timeout );
			
			InetAddress ip;
	    	String hostname = null;
	    	
        	try {
				ip = InetAddress.getLocalHost();
	        	if ( ip != null ) {
	        		hostname = ip.getHostName();
	        		if ( hostname != null ) {
	        			if  ( !( aaiServerList.contains(hostname) ) )
	        				msg = "host name not found in server list " + hostname;
	        		} else
	        			msg = "InetAddress returned null hostname";
	        	} 
			} catch (UnknownHostException e) {
				
				e.printStackTrace();
				msg = "InetAddress getLocalHost exception " + e.getMessage();
        	} 
        	if ( ( msg != null ) && !aaiPrimaryCheck.startsWith( "echo1") ) { //for unit testing
        		logline.add( "unable to check primary ", msg );
        		aaiLogger.info(logline, true, "0");
        		return true;
        	}
        	
        	if (aaiPrimaryCheck.startsWith(echo)) {
        		return amIPrimaryUsingEcho( hostname, aaiPrimaryCheck, aaiServerList );
        	}
        	StringTokenizer st = new StringTokenizer( aaiServerList, "|" );
        	String host;
        	while ( st.hasMoreTokens() ) {
        		host = st.nextToken();
        		try {
        			
        			if (  aaiPrimaryCheck.equals(isReachable)) {
        				ip = InetAddress.getByName(host);
        				if ( ip == null ) {
        	        		logline.add( "getByName failed ", host );
        	        		aaiLogger.info(logline, true, "0");
        	        		return true;
        				}
        				if  ( ip.isReachable(timeout) ) {
        					aaiLogger.debug(logline, "primary is  " + host );
        					if ( host.contains(hostname)) {
        		        		aaiLogger.info(logline, true, "0");
        						return true;
        					}
                    		aaiLogger.info(logline, false, "AAI_4000");
        					return false;
        				} else {
        					aaiLogger.debug(logline, "isReachable false for  "+ host );
        				}
        			} else if (  aaiPrimaryCheck.equals(binPing)) {
        			    List<String> commands = new ArrayList<String>(); 
        			    commands.add("/bin/ping"); 
        			    commands.add("-c"); 
        			    commands.add(aaiPingTimeout); 
        			    commands.add(host); 
        			    int pingResult = doCommand(commands);
        			    if ( pingResult == 0 ) {
        			    	if ( host.contains(hostname)) {
        		        		aaiLogger.info(logline, true, "0");
        			    		return true;
        			    	}
                    		aaiLogger.info(logline, false, "AAI_4000");
        					return false;	
        			    }else {
        					aaiLogger.debug(logline, "pingResult " + pingResult + "for "+ host );
        				}
        			} else if (  aaiPrimaryCheck.equals(netcat)) {
        			    List<String> commands = new ArrayList<String>(); 
        			    commands.add("/usr/bin/nc"); 
        			    commands.add("-w"); 
        			    commands.add(aaiPingCount); // seconds
        			    commands.add(host); 
        			    commands.add("22"); // SCP port
        			    int pingResult = doCommand(commands);
        			    if ( pingResult == 0 ) {
        			    	if ( host.contains(hostname)) {
        			    		aaiLogger.info(logline, true, "0");
        			    		return true;
        			    	}
                    		aaiLogger.info(logline, false, "AAI_4000");
        					return false;	
        			    }else {
        					aaiLogger.debug(logline, "netcat " + pingResult + "for "+ host );
        				}
        			}
        		
        		} catch ( Exception e) {
        			e.printStackTrace();
        			msg = "processing serverList for host " + host + " exception " + e.getMessage();
        			logline.add( "no server found ",  msg );
            		aaiLogger.info(logline, false, "AAI_4000");
            		return false;
        		}
        	}
    		logline.add( "no primary found ",  aaiServerList );
    		aaiLogger.info(logline, false, "AAI_4000");
        	return false;
		}
		
		/**
		 * Which is primary.
		 *
		 * @return the string
		 */
		public String whichIsPrimary() 
		{
			return whichIsPrimary( DEFAULT_CHECK );
		}
		
		/**
		 * Which is primary using echo.
		 *
		 * @param aaiPrimaryCheck the aai primary check
		 * @param aaiServerList the aai server list
		 * @return the string
		 */
		public String whichIsPrimaryUsingEcho( String aaiPrimaryCheck, String aaiServerList) {
			
			String methodName = "whichIsPrimaryUsingEcho";
			
			logline.init("aaigen", transId, fromAppId, methodName);
			
			StringTokenizer st = new StringTokenizer( aaiServerList, "|" );
        	String host;
        	String msg = null;
        	while ( st.hasMoreTokens() ) {
        		host = st.nextToken();
        				
				String portWithEndpoint = aaiPrimaryCheck.substring(aaiPrimaryCheck.indexOf(":"));
				//System.out.println( "using portEndpoint: " + portWithEndpoint);
				Client client = null;
				
				try {
					client = HttpsAuthClient.getTwoWaySSLClient();
				}
				catch (KeyManagementException kme){
					msg = "KeyManagementException in REST call to echo: " + kme.toString();
				} catch (Exception e) {
					msg = " Exception in REST call to echo: " + e.toString();
				}
				
				if ( msg != null ) {
		    		logline.add( "problem using echo " + msg + " no primary found ",  aaiServerList );
		    		aaiLogger.info(logline, false, "AAI_7401");
					return null;
				}
				String resource = "https://" + host + portWithEndpoint;
				WebResource webResource = client
				   .resource(resource);

				try {
					ClientResponse response = webResource.accept("application/json")
					         .header("X-TransactionId", transId)
					         .header("X-FromAppId",  "PrimaryCheck")
			                   .get(ClientResponse.class);
					String output = response.getEntity(String.class);
					if (response.getStatus() != 200) {
						aaiLogger.debug(logline, "echo status " + response.getStatus() + " echo response  "+ output );
						logline.add( "unexpected status ", response.getStatus() );
						aaiLogger.info(logline, false, "AAI_7402");
					} else {
						return host;
					}
				} catch ( Exception e ) {
					logline.add( "exception checking primary ", resource );
					aaiLogger.info(logline, false, "AAI_4000", e);
				}
			}
    		logline.add( "no primary found ",  aaiServerList );
    		aaiLogger.info(logline, false, "AAI_4000");
			return null;
		}
		
		/**
		 * Which is primary.
		 *
		 * @param checkName the check name
		 * @return the string
		 */
		public String whichIsPrimary( String checkName )
		{
			String methodName = "whichIsPrimary";
			
			logline.init("aaigen", transId, fromAppId, methodName);
			String aaiServerList = null;
			String aaiPrimaryCheck = null;
			String aaiPingTimeout = null;
			String aaiPingCount = null;
			
			int timeout = -1;
			
			String msg = null;
			
			try {
				
				aaiServerList = AAIConfig.get(checkName + "serverlist");
				aaiPrimaryCheck = AAIConfig.get(checkName + "primarycheck");

			} catch ( Exception e ) {
				msg = "missing aaiconfig.properties to find primary, returning";
	    		logline.add( "unable to find primary ", msg );
	    		aaiLogger.info(logline, true, "0");
	    		return null;
			}
			
			aaiLogger.debug(logline, "finding primary  "+ aaiPrimaryCheck + " on " + aaiServerList + " timeout " + timeout );
				
	    	return whichIsPrimaryUsingEcho( aaiPrimaryCheck, aaiServerList );
		}
}
