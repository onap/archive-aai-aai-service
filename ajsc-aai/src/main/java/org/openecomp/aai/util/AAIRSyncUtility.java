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

/**
 * 
 */
package org.openecomp.aai.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;

import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;

import com.att.eelf.configuration.EELFLogger;


public class AAIRSyncUtility {

	protected static AAILogger aaiLogger = new AAILogger(AAIRSyncUtility.class.getName());
	protected LogLine logline = new LogLine();
	public EELFLogger logger;
	
	private final String DEFAULT_CHECK = new String("aai.primary.filetransfer.");
	
	/**
	 * Instantiates a new AAIR sync utility.
	 */
	public AAIRSyncUtility() {

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
	 * Method sendRsyncCommand.
	 *
	 * @param transId the trans id
	 * @param fileName the file name
	 */
	public void sendRsyncCommand(String transId, String fileName) 
	{
		String methodName = "sendRsyncCommand";
		
		logline.init("aaigen", transId, "AAI-Tools", methodName);
		String aaiServerList = null;
		String rsyncOptionsList = null;
		String msg = null;
		
		try {
			
			aaiServerList = AAIConfig.get(DEFAULT_CHECK + "serverlist");
			rsyncOptionsList = AAIConfig.get("aai.rsync.options.list");
			String enableRsync = AAIConfig.get("aai.rsync.enabled");
			
			if (!AAIConfig.isEmpty(enableRsync) && "n".equalsIgnoreCase(enableRsync)){
				msg = " rsync is not enabled in aaiconfig.properties ";
	    		logline.add( "rsync not invoked for "+fileName, msg );
	    		aaiLogger.info(logline, true, "0");
				return;
			}
		} catch ( Exception e ) {
			msg = "missing aaiconfig.properties entries for rsync";
    		logline.add( "rsync not invoked ", msg );
    		aaiLogger.info(logline, true, "0");
		}
		
		aaiLogger.debug(logline, "rsync to copy files started....");
		
    	ArrayList<String> remoteHostList = new ArrayList<String>();
    	StringTokenizer serverList = new StringTokenizer( aaiServerList, "|" );
    	String host = null;
		try {
			host = getHost();
			String remoteConnString = null;
			 
			remoteHostList = getRemoteHostList(serverList, host);
			aaiLogger.debug(logline, " this host:" + host);
	    	String pickUpDirectory = AAIConfig.get("instar.pickup.dir");
	    	String user = AAIConfig.get("aai.rsync.remote.user"); 
	    	String rsyncCmd = AAIConfig.get("aai.rsync.command");
	    	
	    	//Push: rsync [OPTION...] SRC... [USER@]HOST:DEST
	    	
	    	java.util.Iterator<String> remoteHostItr = remoteHostList.iterator();
	    	while (!remoteHostList.isEmpty() && remoteHostItr.hasNext()) {
	    		String remoteHost = remoteHostItr.next();
	    		remoteConnString =user+"@"+remoteHost+":"+pickUpDirectory;
				   
				List<String> commands = new ArrayList<String>(); 
			    commands.add(rsyncCmd); 
			    StringTokenizer optionTks = new StringTokenizer( rsyncOptionsList, "|" );
			    while (optionTks.hasMoreTokens()){
			    	commands.add(optionTks.nextToken());
			    }
			    commands.add(fileName); // src directory/fileName
			    commands.add(remoteConnString); // target username/host/path
			    aaiLogger.debug(logline,  commands.toString());
			    int rsyncResult = doCommand(commands);
			    if ( rsyncResult == 0 ) {
			    	aaiLogger.debug(logline, "rsync completed for "+remoteHost);
			    }else {
					aaiLogger.debug(logline, "rsync failed for "+ remoteHost+ " with response code "+rsyncResult );
				}
			} 
    	} catch ( Exception e) {
			e.printStackTrace();
			msg = "processing serverList for host " + host + " exception " + e.getMessage();
			logline.add( "no server found ",  msg );
    		aaiLogger.info(logline, false, "AAI_4000");
		}
		aaiLogger.info(logline, true, "0");
	}

	/**
	 * Gets the remote host list.
	 *
	 * @param serverList the server list
	 * @param host the host
	 * @return the remote host list
	 */
	private ArrayList<String> getRemoteHostList(StringTokenizer serverList, String host) {
		ArrayList<String> remoteHostList = new ArrayList<String>();
		String remoteHost = null;
		while ( serverList.hasMoreTokens() ) {
    		remoteHost = serverList.nextToken();
    		if (!host.equalsIgnoreCase(remoteHost)){
    			remoteHostList.add(remoteHost);
    		}
		}
		return remoteHostList;
	}

	/**
	 * Gets the host.
	 *
	 * @return the host
	 * @throws AAIException the AAI exception
	 */
	private String getHost() throws AAIException {
		String aaiServerList = AAIConfig.get(DEFAULT_CHECK + "serverlist");
		String msg = null;
		String hostname = null;
		try {
			InetAddress ip = InetAddress.getLocalHost();
        	if ( ip != null ) {
        		hostname = ip.getHostName();
        		if ( hostname != null ) {
        			if  ( !( aaiServerList.contains(hostname) ) )
        				msg = "host name not found in server list " + hostname;
        		} else
        			msg = "InetAddress returned null hostname";
        	} 
        	 
		} catch (UnknownHostException e) {
			msg = "InetAddress getLocalHost exception " + e.getMessage();
			aaiLogger.debug(logline, msg);
    	}
		if ( msg != null ) {
    		aaiLogger.info(logline, true, "0");
    	}
		return hostname;
	}
	
}
