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

import java.time.Instant;

import static org.wso2.micro.integrator.dataservices.core.analytics.DataServicesAnalyticsConstants.*;

/**
 * Emits Data Service analytics events in the canonical envelope used by
 * every other entity type (API, Proxy, Sequence, Endpoint, Inbound Endpoint).
 *
 * <p>JSON shape (matches what {@code ElasticDataSchema} emits for API events):
 * <pre>
 * {
 *   "serverInfo": { "hostname": "...", "serverName": "...", "ipAddress": "...", "id": "..." },
 *   "timestamp": "2026-05-19T04:29:33.905Z",
 *   "schemaVersion": 1,
 *   "payload": {
 *     "metadata": { ... optional, holds DS-specific error info on failure ... },
 *     "entityType": "DATA_SERVICE",
 *     "entityClassName": "DATA_SERVICE",
 *     "failure": false,
 *     "faultResponse": false,
 *     "latency": 79,
 *     "messageId": "urn:uuid:...",
 *     "correlation_id": "...",        // optional
 *     "dataServiceDetails": {
 *       "name":      "EmployeeDataService",
 *       "operation": "_getemployee_employeenumber",
 *       "subType":   "DATA_SERVICE" | "ODATA" | "DSS_CALL_MEDIATOR"
 *     },
 *     "httpMethod":     "GET",        // optional
 *     "httpUrl":        "/services/...",
 *     "httpStatusCode": "200",
 *     "remoteHost":     "10.0.0.5"
 *   }
 * }
 * </pre>
 *
 * <p>The root {@code timestamp} is an ISO 8601 string (the Kibana index
 * pattern's {@code timeFieldName}); changing it will break every dashboard.
 *
 * <p>This class deliberately does <strong>not</strong> depend on
 * {@code ElasticStatisticsPublisher} or {@code ElasticDataSchema} at compile
 * time: doing so would re-introduce the Maven cycle
 * ({@code publisher → initializer → dataservices.core → publisher}).
 * Instead, it borrows the publisher's logger name so log4j routes the line
 * through the same {@code ELK_ANALYTICS_APPENDER} that writes
 * {@code synapse-analytics.log}, and it prepends the configured analytics
 * prefix so Filebeat's existing pipeline ingests the line unchanged.
 */
public class DataServicesAnalyticsPublisher {

    /**
     * Logger name MUST match the production {@code ElasticStatisticsPublisher}
     * class so log4j routes DS analytics through the same appender as the
     * Synapse-side analytics.
     */
    private static final String ELASTIC_PUBLISHER_LOGGER_NAME =
            "org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticStatisticsPublisher";

    private static final Log analyticsLog = LogFactory.getLog(ELASTIC_PUBLISHER_LOGGER_NAME);
    private static final Log log          = LogFactory.getLog(DataServicesAnalyticsPublisher.class);

    private DataServicesAnalyticsPublisher() {}

    public static void publish(DataServiceAnalyticsEvent event) {
        if (event == null) return;
        try {
            JSONObject root = buildJson(event);
            String prefix = AnalyticsConfig.getAnalyticsPrefix();
            analyticsLog.info(String.format("%s %s", prefix, root.toString()));
        } catch (Throwable t) {
            // Never propagate analytics failures into the main flow.
            log.warn("Failed to publish DS analytics event: " + t.getMessage(), t);
        }
    }

    /**
     * Builds the canonical analytics envelope JSON for the given event.
     * Package-private so tests can verify the schema without going through
     * the Log4j appender.
     */
    static JSONObject buildJson(DataServiceAnalyticsEvent event) {
        boolean isFailure = STATUS_FAILURE.equals(event.getStatus());

        JSONObject details = new JSONObject();
        putIfNotNull(details, FIELD_DETAILS_NAME,      event.getServiceName());
        putIfNotNull(details, FIELD_DETAILS_OPERATION, event.getOperationName());
        putIfNotNull(details, FIELD_DETAILS_SUB_TYPE,  event.getSubType());

        JSONObject metadata = new JSONObject();
        if (isFailure) {
            putIfNotNull(metadata, METADATA_ERROR_CODE,    event.getErrorCode());
            putIfNotNull(metadata, METADATA_ERROR_MESSAGE, event.getErrorMessage());
        }

        JSONObject payload = new JSONObject();
        payload.put(FIELD_METADATA,             metadata);
        payload.put(FIELD_ENTITY_TYPE,          ENTITY_TYPE_DATA_SERVICE);
        payload.put(FIELD_ENTITY_CLASS_NAME,    ENTITY_CLASS_NAME_DATA_SERVICE);
        payload.put(FIELD_FAILURE,              isFailure);
        payload.put(FIELD_FAULT_RESPONSE,       isFailure);
        payload.put(FIELD_LATENCY,              event.getLatency());
        payload.put(FIELD_DATA_SERVICE_DETAILS, details);

        putIfNotNull(payload, FIELD_MESSAGE_ID,       event.getMessageId());
        putIfNotNull(payload, FIELD_CORRELATION_ID,   event.getCorrelationId());
        putIfNotNull(payload, FIELD_HTTP_METHOD,      event.getHttpMethod());
        putIfNotNull(payload, FIELD_HTTP_URL,         event.getHttpUrl());
        putIfNotNull(payload, FIELD_HTTP_STATUS_CODE, event.getHttpStatusCode());
        putIfNotNull(payload, FIELD_REMOTE_HOST,      event.getRemoteHost());

        JSONObject root = new JSONObject();
        root.put(FIELD_SERVER_INFO,    ServerInfoProvider.getServerInfo());
        root.put(FIELD_TIMESTAMP,      toIsoTimestamp(event.getTimestamp()));
        root.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        root.put(FIELD_PAYLOAD,        payload);
        return root;
    }

    private static String toIsoTimestamp(long epochMillis) {
        if (epochMillis <= 0L) {
            return Instant.now().toString();
        }
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    private static void putIfNotNull(JSONObject obj, String key, Object value) {
        if (value != null) {
            obj.put(key, value);
        }
    }
}
