/*
 * Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.dataservices.core.analytics;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the same Synapse analytics configuration that the existing
 * {@code ElasticStatisticsPublisher} reads, without taking a compile-time
 * dependency on {@code synapse-core} or on the analytics publisher module
 * (either would create a Maven cycle).
 *
 * <p>Source of truth: {@code ${carbon.home}/conf/synapse.properties} —
 * the same file that {@code SynapsePropertiesLoader} loads at runtime.
 * Deployment-toml renders {@code [analytics]} entries into this file,
 * so this helper sees the same values the production publisher sees.
 *
 * <p>JVM system properties (e.g. {@code -Danalytics.enabled=true})
 * win over the file when both are present, matching the convention
 * used elsewhere in MI.
 *
 * <p>Loaded lazily on first access and cached for the JVM lifetime.
 * Restart MI to pick up config changes (same constraint as the Synapse-side
 * publisher).
 */
final class AnalyticsConfig {

    private static final Log log = LogFactory.getLog(AnalyticsConfig.class);

    private static final String SYNAPSE_PROPERTIES_PATH = "conf/synapse.properties";
    private static final String CARBON_HOME_PROPERTY    = "carbon.home";

    static final String ANALYTICS_ENABLED_KEY = "analytics.enabled";
    static final String ANALYTICS_PREFIX_KEY  = "analytics.prefix";

    static final String DEFAULT_ANALYTICS_PREFIX = "SYNAPSE_ANALYTICS_DATA";

    private static volatile Properties cachedProperties;

    private AnalyticsConfig() {}

    static boolean isAnalyticsEnabled() {
        return Boolean.parseBoolean(getProperty(ANALYTICS_ENABLED_KEY, "false"));
    }

    static String getAnalyticsPrefix() {
        return getProperty(ANALYTICS_PREFIX_KEY, DEFAULT_ANALYTICS_PREFIX);
    }

    /**
     * Returns the value of {@code key}, checking JVM system properties first
     * and {@code synapse.properties} second.
     */
    static String getProperty(String key, String defaultValue) {
        String fromJvm = System.getProperty(key);
        if (fromJvm != null) {
            return fromJvm;
        }
        Properties props = loadSynapseProperties();
        return props.getProperty(key, defaultValue);
    }

    private static Properties loadSynapseProperties() {
        Properties result = cachedProperties;
        if (result != null) {
            return result;
        }
        synchronized (AnalyticsConfig.class) {
            if (cachedProperties != null) {
                return cachedProperties;
            }
            Properties props = new Properties();
            String carbonHome = System.getProperty(CARBON_HOME_PROPERTY);
            if (carbonHome == null) {
                if (log.isDebugEnabled()) {
                    log.debug("carbon.home system property is not set; DS analytics will "
                            + "fall back to JVM system properties / defaults.");
                }
                cachedProperties = props;
                return props;
            }
            File propsFile = new File(carbonHome, SYNAPSE_PROPERTIES_PATH);
            if (!propsFile.isFile()) {
                if (log.isDebugEnabled()) {
                    log.debug("synapse.properties not found at " + propsFile.getAbsolutePath()
                            + "; DS analytics will fall back to JVM system properties / defaults.");
                }
                cachedProperties = props;
                return props;
            }
            try (InputStream in = new FileInputStream(propsFile)) {
                props.load(in);
            } catch (Exception e) {
                log.warn("Failed to load " + propsFile.getAbsolutePath()
                        + " for DS analytics config: " + e.getMessage());
            }
            cachedProperties = props;
            return props;
        }
    }
}
