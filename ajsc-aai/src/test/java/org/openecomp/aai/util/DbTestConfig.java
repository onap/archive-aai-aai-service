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

import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.TimerTask;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Timer;
import java.net.InetAddress;

public class DbTestConfig {

	public static final String AUDIT_FILESEP = (System.getProperty("file.separator") == null) ? "/" : System.getProperty("file.separator");
	public static final String AUDIT_HOME = (System.getProperty("audit.home") == null) ? AUDIT_FILESEP + "opt" + AUDIT_FILESEP + "audit" : System.getProperty("audit.home"); 
	public static final String AuditPropFilename = "c:\\tmp\\auditConfig.prop";
	public static final String AUDIT_CONFIG_CHECKINGTIME = "audit.config.checktime";
	public static final String AUDIT_NODENAME = "localhost";
	public static final String AUDIT_DEBUG = "audit.config.debug";
	
	private static Properties serverProps;
    private static boolean propsInitialized = false;
    private static boolean timerSet = false;
    private static Timer timer = null;
    
    private static String propFile = null;

    /**
     * Inits the.
     *
     * @param propertyFile the property file
     */
    public synchronized static void init(String propertyFile) {
    	propFile = propertyFile;
    	init();
    }
    
	/**
	 * Inits the.
	 */
	public synchronized static void init() {
		  System.out.println("Initializing Config");
				
		  DbTestConfig.getConfigFile();
		  DbTestConfig.reloadConfig();
		  
		  if ( propFile == null)
			  propFile = AuditPropFilename;
		  TimerTask task = null;
		  task = new DbTestFileWatcher ( new File(propFile)) {
			  protected void onChange( File file ) {
				  // here we implement the onChange
				  DbTestConfig.reloadConfig();
			  }
		  };
		  
	      if (!timerSet) {
	          timerSet = true;
	          // repeat the check every second
	          timer = new Timer();
	          String fwi = DbTestConfig.get(AUDIT_CONFIG_CHECKINGTIME);
	          timer.schedule( task , new Date(), Integer.parseInt(fwi) );
	          System.out.println("Config Watcher Interval=" + fwi);

	          System.out.println("File" + propFile+" Loaded!");
	       }       

		}

	    /**
    	 * Cleanup.
    	 */
    	public static void cleanup() {
			timer.cancel();
		}

	    /**
    	 * Gets the config file.
    	 *
    	 * @return the config file
    	 */
    	public static String getConfigFile() {
	        return propFile;
	    }

    /**
     * Reload config.
     */
    public synchronized static void reloadConfig() {

        String propFileName = propFile;

        Properties newServerProps = null;
        
        System.out.println("Reloading config from "+propFileName);
        
        try {
            InputStream is = new FileInputStream(propFileName);
            newServerProps = new Properties();
            newServerProps.load(is);
            propsInitialized = true;

            serverProps = newServerProps;
            if (get(AUDIT_DEBUG).equals("on")) {
            	serverProps.list(System.out);
            }
            newServerProps = null;
        	
        } catch (FileNotFoundException fnfe) {
        	System.out.println("AuditConfig: " + propFileName + ". FileNotFoundException: "+fnfe.getMessage());
        } catch (IOException e) {
        	System.out.println("AuditConfig: " + propFileName + ". IOException: "+e.getMessage());
        }
    }
    
    /**
     * Gets the.
     *
     * @param key the key
     * @param defaultValue the default value
     * @return the string
     */
    public static String get(String key, String defaultValue) {
    	String result = defaultValue;
    	try {
    		result = get (key);
    	}
    	catch ( Exception a ) {
    	}
    	return result;
    }

    /**
     * Gets the.
     *
     * @param key the key
     * @return the string
     */
    public static String get(String key) {
    	String response = null;
    	
    	if (key.equals(AUDIT_NODENAME)) {
    		// Get this from InetAddress rather than the properties file
    		String nodeName = getNodeName();
    		if (nodeName != null) {
    			return nodeName;
    		}
    		// else get from property file
    	}
    	
    	if (!propsInitialized || (serverProps == null)) {
    		reloadConfig();
    	}
    	if (!serverProps.containsKey(key)) {
    		System.out.println( "Property key "+key+" cannot be found");
    	} else {
    		response = serverProps.getProperty(key);
    		if (response == null || response.isEmpty()) {
    			System.out.println("Property key "+key+" is null or empty");
    		}
    	}
    	return response;
	}

    /**
     * Gets the int.
     *
     * @param key the key
     * @return the int
     */
    public static int getInt(String key) {
    	return Integer.valueOf(DbTestConfig.get(key));
	}

	/**
	 * Gets the server props.
	 *
	 * @return the server props
	 */
	public static Properties getServerProps() {
		return serverProps;
	}
	
	/**
	 * Gets the node name.
	 *
	 * @return the node name
	 */
	public static String getNodeName() {
		try {
            InetAddress ip = InetAddress.getLocalHost();
            if (ip != null) {
                   String hostname = ip.getHostName();
                   if (hostname != null) {
                	   return hostname;
                   }
            }
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	  /**
	   * Extracts a specific property key subset from the known properties.
	   * The prefix may be removed from the keys in the resulting dictionary,
	   * or it may be kept. In the latter case, exact matches on the prefix
	   * will also be copied into the resulting dictionary.
	   *
	   * @param prefix is the key prefix to filter the properties by.
	   * @param keepPrefix if true, the key prefix is kept in the resulting
	   * dictionary. As side-effect, a key that matches the prefix exactly
	   * will also be copied. If false, the resulting dictionary's keys are
	   * shortened by the prefix. An exact prefix match will not be copied,
	   * as it would result in an empty string key.
	   * @return a property dictionary matching the filter key. May be
	   * an empty dictionary, if no prefix matches were found.
	   *
	   * @see #getProperty( String ) is used to assemble matches
	   */
	  public static Properties matchingSubset(String prefix, boolean keepPrefix) {
	    Properties result = new Properties();

	    // sanity check
	    if (prefix == null || prefix.length() == 0) {
	      return result;
	    }

	    String prefixMatch; // match prefix strings with this
	    String prefixSelf; // match self with this
	    if (prefix.charAt(prefix.length() - 1) != '.') {
	      // prefix does not end in a dot
	      prefixSelf = prefix;
	      prefixMatch = prefix + '.';
	    } else {
	      // prefix does end in one dot, remove for exact matches
	      prefixSelf = prefix.substring(0, prefix.length() - 1);
	      prefixMatch = prefix;
	    }
	    // POSTCONDITION: prefixMatch and prefixSelf are initialized!

	    // now add all matches into the resulting properties.
	    // Remark 1: #propertyNames() will contain the System properties!
	    // Remark 2: We need to give priority to System properties. This is done
	    // automatically by calling this class's getProperty method.
	    String key;
	    for (Enumeration e = serverProps.keys(); e.hasMoreElements(); ) {
	      key = (String) e.nextElement();

	      if (keepPrefix) {
	        // keep full prefix in result, also copy direct matches
	        if (key.startsWith(prefixMatch) || key.equals(prefixSelf)) {
	          result.setProperty(key, serverProps.getProperty(key));
	        }
	      } else {
	        // remove full prefix in result, dont copy direct matches
	        if (key.startsWith(prefixMatch)) {
	          result.setProperty(key.substring(prefixMatch.length()), serverProps.getProperty(key));
	        }
	      }
	    }

	    // done
	    return result;
	  }



	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		DbTestConfig.init(  );

	}

}
