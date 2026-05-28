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
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Computes the {@code serverInfo} envelope block ({@code hostname},
 * {@code serverName}, {@code ipAddress}, {@code id}) using the same field
 * values the Synapse-side {@code ElasticDataSchema} emits for API/Proxy events.
 *
 * <p>We cannot call {@code ServerConfigurationInformation} or
 * {@code ServiceBusInitializer} directly without re-introducing the Maven
 * cycle described in {@link DataServicesAnalyticsPublisher}, so values are
 * resolved as follows:
 * <ul>
 *   <li>{@code hostname} — {@link InetAddress#getLocalHost()} (matches what
 *       {@code ServerConfigurationInformation} stores in practice).</li>
 *   <li>{@code ipAddress} — {@link InetAddress#getLocalHost()} address.</li>
 *   <li>{@code serverName} — {@code carbon.name} system property, falling
 *       back to {@code "localhost"} (the Synapse default).</li>
 *   <li>{@code id} — {@code analytics.id} from {@code synapse.properties},
 *       falling back to {@code hostname}, matching {@code ElasticDataSchema.init()}.</li>
 * </ul>
 * Cached for the JVM lifetime — restart MI to pick up changes.
 */
final class ServerInfoProvider {

    private static final Log log = LogFactory.getLog(ServerInfoProvider.class);

    private static final String CARBON_NAME_PROPERTY = "carbon.name";
    private static final String ANALYTICS_ID_KEY     = "analytics.id";

    private static volatile JSONObject cachedServerInfo;

    private ServerInfoProvider() {}

    static JSONObject getServerInfo() {
        JSONObject result = cachedServerInfo;
        if (result != null) {
            return result;
        }
        synchronized (ServerInfoProvider.class) {
            if (cachedServerInfo != null) {
                return cachedServerInfo;
            }
            String hostname = resolveHostName();
            String ipAddress = resolveIpAddress();
            String serverName = System.getProperty(CARBON_NAME_PROPERTY, "localhost");
            String publisherId = AnalyticsConfig.getProperty(ANALYTICS_ID_KEY, hostname);

            JSONObject info = new JSONObject();
            putIfNotNull(info, "hostname",   hostname);
            putIfNotNull(info, "serverName", serverName);
            putIfNotNull(info, "ipAddress",  ipAddress);
            putIfNotNull(info, "id",         publisherId);
            cachedServerInfo = info;
            return info;
        }
    }

    private static String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.debug("Unable to resolve local hostname for DS analytics serverInfo: " + e.getMessage());
            return null;
        }
    }

    private static String resolveIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.debug("Unable to resolve local IP for DS analytics serverInfo: " + e.getMessage());
            return null;
        }
    }

    private static void putIfNotNull(JSONObject obj, String key, String value) {
        if (value != null) {
            obj.put(key, value);
        }
    }
}
