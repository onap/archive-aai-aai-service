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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DbTestProcessBuilder {
	///public static Logger clog = Logger.getLogger("auditConsole");
	//public static Logger alog = Logger.getLogger("auditLog");
		
	/**
	 * Start audit process non blocking.
	 *
	 * @param wait the wait
	 * @param cmds the cmds
	 * @param dir the dir
	 */
	public void startAuditProcessNonBlocking(final long wait, final String cmds[], final String dir) {

		final ProcessBuilder pb = new ProcessBuilder(cmds).redirectErrorStream(true).directory(new File(dir));
		
		new Thread(new Runnable() {
			public void run() {
				try {
					//System.out.println( "sleeping seconds " + wait + " cmds " + Arrays.toString(cmds));
                	Thread.sleep(wait*1000);
                	//System.out.println( "returned from sleep");
                    final Process p = pb.start();
                    //System.out.println( "returned from pb.start");
            		final InputStream is = p.getInputStream();
            		final BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            		final InputStreamReader isr = new InputStreamReader(is);
            		final BufferedReader br = new BufferedReader(isr);
//clog.debug("Output of running " + Arrays.toString(cmds) + " is:");
            		System.out.println("Output of running  is:" );
            		String line;
            		while ((line = br.readLine()) != null ) {
            			System.out.println(line);
            		}
            		System.out.println("stderr of running is:" );
            		
            		while ((line = stdError.readLine()) != null ) {
            			System.out.println(line);
            		}
            		
            	} catch (IOException ie) {
            			
            	} catch (InterruptedException itre) {
                    Thread.currentThread().interrupt();
           		}
			}
		}).start();
    		
	}
	

	private final ScheduledExecutorService auditProcessScheduler =
			Executors.newScheduledThreadPool(10);
		
	/**
	 * Run W command every X seconds for Y minutes.
	 *
	 * @param w the w
	 * @param x the x
	 * @param y the y
	 * @param runningDir the running dir
	 */
	public void runWCommandEveryXSecondsForYMinutes(String[] w, int x, int y, final String runningDir) {
		final String[] c1 = w;
		final Runnable audit = new Runnable() {
			public void run() { 
//clog.debug("checkpoint "+(new Date()).toString());
				DbTestProcessBuilder a1 = new DbTestProcessBuilder();
				a1.startAuditProcessNonBlocking(1, c1, "/tmp");
			}
		};
		
		final ScheduledFuture<?> auditHandle =
				auditProcessScheduler.scheduleAtFixedRate(audit, 0, x, TimeUnit.SECONDS);
		auditProcessScheduler.schedule(new Runnable() {
			public void run() { 
				auditHandle.cancel(true);
			}
		}, y * 60, TimeUnit.SECONDS);
	}
	

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	@SuppressWarnings({ "null", "static-access" })
	public static void main(String args[]) {
		String props = "NA";
		if  (args.length > 0) {
			System.out.println( "DbTestProcessBuilder called with " + args.length + " arguments, " + args[0]);
			props = args[0].trim(); 
		} else {
			System.out.print("usage: DbTestProcessBuilder <auditConfig.prop path\n");
			return;
		}
		DbTestConfig.init(props);
		String ail = DbTestConfig.get("audit.list"); 	
		String path = DbTestConfig.get("audit.path");
		final String runningDir = DbTestConfig.get("audit.runningdir");
		try {
			DbTestGetFileTime getFileTime = new DbTestGetFileTime();
			FileTime fileTime = getFileTime.createFileReturnTime( path );
			System.out.println(path + " creation time :"
		        + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
		                   .format(fileTime.toMillis()) + " runningDir " + runningDir);
		} catch ( IOException io ) {
			System.out.println( "IOException getting creation time " + path + " message " + io.getMessage());
			io.printStackTrace();
		}
		
		List<String> items = Arrays.asList(ail.split("\\s*,\\s*"));	
		for (String ai: items) {
			if (!DbTestConfig.get("audit.task."+ai+".status").startsWith("a")) {
				continue;
			}
//clog.debug("***audit item = " + ai + " Starting***");
			
			String w1 = DbTestConfig.get("audit.task."+ai+".cmd");
			String[] w2 = w1.split("\\s*,\\s*");
			System.out.print( "task items are : " + Arrays.toString(w2));
			// append the audit item name as the prefix of the audit directory name
			/*final int N = w2.length;
			w2 = Arrays.copyOf(w2, N+1);
			w2[N-2] = "\"-Dp=" + DbTestConfig.get("audit.task.odl.output.dir")+ai + "\"";
//clog.debug("***java -D:"+w2[N-2]);
			//w2[N] = "\""+DbTestConfig.get("audit.task.odl.output.dir")+ai+"\"";
			w2[N] = "\""+DbTestConfig.get("audit.task.odl.output.dir")+ai+"\"";
			*/
	  		DbTestProcessBuilder apb = new DbTestProcessBuilder();
	  		
	  		String ts1 = DbTestConfig.get("audit.task."+ai+".schedule");
	  		String[] ts2 = ts1.split("\\s*,\\s*");
	  		// note ts2[0] is the wait-before time, and it is not being used right now. We start with ts2[1]
	  		apb.runWCommandEveryXSecondsForYMinutes(w2,Integer.parseInt(ts2[1]),Integer.parseInt(ts2[2]), runningDir);
//clog.debug("***audit item = " + ai + " started***");
	  		System.out.println( "started test " + ai);

			/*
			int ct = 0;
			
			while (true) try {
				if (DbTestConfig.get("jcl").startsWith("q")) {
					System.out.println("***Audit Main Program exiting...");
					System.exit(0);
				}
				
				Thread.currentThread().sleep(1000);
				if (ct < 10) {
					ct++;
				} else {
					//clog.debug(AuditConfig.get("jcl").charAt(0));
					ct=0;
				}

			} catch (InterruptedException ie) {
				
			} */
		}
		int ct = 0;
		
		while (true) try {
			if (DbTestConfig.get("jcl").startsWith("q")) {
				System.out.println("***Audit Main Program exiting...");
				System.exit(0);
			}
			
			Thread.currentThread().sleep(1000);
			if (ct < 10) {
				ct++;
			} else {
				//clog.debug(AuditConfig.get("jcl").charAt(0));
				ct=0;
			}

		} catch (InterruptedException ie) {
			
		}

	}

}
