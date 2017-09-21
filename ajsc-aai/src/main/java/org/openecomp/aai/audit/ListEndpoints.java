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

package org.openecomp.aai.audit;

import com.google.common.base.CaseFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorFactory;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.LogLineBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class ListEndpoints.
 */
public class ListEndpoints {


    private static final Logger log = LoggerFactory.getLogger(ListEndpoints.class);
    private static final String INVENTORY = "inventory";

    private static final String[] blacklist = {"search", "aai-internal", "models", "named-queries"};

    private List<String> endpoints = new ArrayList<>();

    private Map<String, String> endpointToLogicalName = new HashMap<>();

    private final LogLineBuilder llBuilder = new LogLineBuilder();

    /**
     * Instantiates a new list endpoints.
     *
     * @param version the version
     */
    public ListEndpoints(Version version) {

        Loader loader = LoaderFactory.createLoaderForVersion(ModelType.MOXY, version, llBuilder);
        if (loader != null) {
            Introspector introspector = loader.introspectorFromName(INVENTORY);

            beginAudit(introspector, "/aai/" + version);
        } else {
            log.error(String.format("failed to create logger for %s version", version));
        }
    }

    /**
     * Begin audit.
     *
     * @param obj the obj
     * @param uri the uri
     */
    private void beginAudit(Introspector obj, String uri) {
        String currentUri = getCurrentUri(obj, uri);

        if (obj.getName().equals("relationship-data") || obj.getName().equals("related-to-property")) {
            return;
        }
        if (!obj.isContainer()) {
            endpoints.add(currentUri);
        }

        populateLogicalName(obj, uri, currentUri);

        handleAudit(obj, currentUri);
    }

    private String getCurrentUri(Introspector obj, String uri) {
        if (!obj.getDbName().equals(INVENTORY)) {
            return uri + obj.getGenericURI();
        }
        return uri;
    }

    private void handleAudit(Introspector obj, String currentUri) {
        outer:
        for (String propName : obj.getProperties()) {
            for (String item : blacklist) {
                if (propName.equals(item)) {
                    continue outer;
                }
            }
            if (obj.isListType(propName)) {
                if (obj.isComplexGenericType(propName)) {
                    beginAudit(
                        IntrospectorFactory
                            .newInstance(ModelType.MOXY, obj.newInstanceOfNestedProperty(propName), llBuilder),
                        currentUri);
                }
            } else if (obj.isComplexType(propName)) {
                beginAudit(
                    IntrospectorFactory.newInstance(ModelType.MOXY, obj.newInstanceOfProperty(propName), llBuilder),
                    currentUri);
            }
        }
    }

    /**
     * Populate logical name.
     *
     * @param obj the obj
     * @param uriString the uri
     * @param currentUri the current uri
     */
    private void populateLogicalName(Introspector obj, String uriString, String currentUri) {

        if (obj.getDbName().equals(INVENTORY) || currentUri.split("/").length <= 4 || currentUri
            .endsWith("relationship-list")) {
            return;
        }
        String uri = "";
        if (uriString.endsWith("/relationship-list")) {
            uri = uriString.substring(0, uriString.lastIndexOf('/'));
        }

        String logicalName;
        String keys = "";

        if (!obj.getAllKeys().isEmpty()) {

            Pattern p = Pattern.compile("/\\{[\\w\\d\\-]+\\}/\\{[\\w\\d\\-]+\\}+$");
            Matcher m = p.matcher(currentUri);

            if (m.find()) {
                keys = StringUtils.join(obj.getAllKeys(), "-and-");
            } else {
                keys = StringUtils.join(obj.getAllKeys(), "-or-");
            }
            keys = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, keys);
            if (!keys.isEmpty()) {
                keys = "With" + keys;
            }
        }

        logicalName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, obj.getDbName()) + keys;

        if (endpointToLogicalName.containsKey(uri) && uri.endsWith("}")) {
            logicalName = logicalName + "From" + endpointToLogicalName.get(uri);
        } else if (endpointToLogicalName.containsKey(uri.substring(0, uri.lastIndexOf('/')))) {
            logicalName = logicalName + "From" + endpointToLogicalName.get(uri.substring(0, uri.lastIndexOf('/')));
        }

        endpointToLogicalName.put(currentUri, logicalName);
    }

    /**
     * Gets the logical names.
     *
     * @return the logical names
     */
    public Map<String, String> getLogicalNames() {

        return endpointToLogicalName;
    }

    /**
     * Gets the endpoints.
     *
     * @return the endpoints
     */
    public List<String> getEndpoints() {

        return this.getEndpoints("");
    }

    /**
     * Gets the endpoints.
     *
     * @param filterOut the filter out
     * @return the endpoints
     */
    public List<String> getEndpoints(String filterOut) {
        List<String> result = new ArrayList<>();
        Pattern p = null;
        Matcher m;
        if (!"".equals(filterOut)) {
            p = Pattern.compile(filterOut);
        }
        for (String s : endpoints) {
            if (p != null) {
                m = p.matcher(s);
                if (m.find()) {
                    continue;
                }
            }
            result.add(s);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String s : endpoints) {
            sb.append(s).append("\n");
        }
        return sb.toString();
    }

    /**
     * To string.
     *
     * @param filterOut the filter out
     * @return the string
     */
    public String toString(String filterOut) {
        StringBuilder sb = new StringBuilder();
        Pattern p = Pattern.compile(filterOut);
        Matcher m;
        for (String s : endpoints) {
            m = p.matcher(s);
            if (!m.find()) {
                sb.append(s).append("\n");
            }
        }
        return sb.toString();
    }
}
