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


/**
 * Utility to convert data from one form to another
  *
 */
public class DataConversionHelper {
	
	public static final String IPVERSION_IPV4 = "ipv4";
	public static final String IPVERSION_IPV6 = "ipv6";
	public static final String IPVERSION_UNKNOWN = "unknown";

	/**
	 * Instantiates a new data conversion helper.
	 */
	public DataConversionHelper() { }
	
	/** 
	 * Convert from 4 or 6 to ipv4 or ipv6.  Returns unknown if 4 or 6 not passed.
	 * @param numVal expects good input but won't error if that's not what's passed
	 * @return IPVERSION constant, .
	 * @see org.openecomp.aai.domain.yang.IpVersion
	 */
	public static String convertIPVersionNumToString(String numVal) {
			if ("4".equals(numVal)) return IPVERSION_IPV4;
			else if ("6".equals(numVal))return IPVERSION_IPV6;
			else return IPVERSION_UNKNOWN;
		}
		
	/** 
	 * Convert from ipv4 or ipv6 to 4 or 6.  Returns 0 on bad input.
	 * @param stringVal expects good input but won't error if that's not what's passed
	 * @return 4 or 6, or 0 if a bad string is sent.
	 * @see org.openecomp.aai.domain.yang.IpVersion
	 */
	public static String convertIPVersionStringToNum(String stringVal) {
			if (IPVERSION_IPV4.equals(stringVal)) return "4";
			else if (IPVERSION_IPV6.equals(stringVal)) return "6";
			else return "0";
		}

}
