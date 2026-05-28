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

import junit.framework.TestCase;
import org.json.JSONObject;

import java.time.Instant;

/**
 * Locks in the JSON envelope that every Kibana dashboard depends on.
 * If a panel ever stops rendering DS data, run these tests first — they prove
 * the wire format is intact.
 */
public class DataServicesAnalyticsPublisherTestSuite extends TestCase {

    private DataServiceAnalyticsEvent event;

    @Override
    protected void setUp() {
        event = new DataServiceAnalyticsEvent();
        event.setServiceName("SampleDataService");
        event.setOperationName("urn:listEmployees");
        event.setSubType(DataServicesAnalyticsConstants.SUB_TYPE_DATA_SERVICE);
        event.setLatency(42L);
        event.setStatus(DataServicesAnalyticsConstants.STATUS_SUCCESS);
        event.setTimestamp(1779164899202L);
        event.setMessageId("urn:uuid:test-1");
        event.setHttpMethod("POST");
        event.setHttpUrl("/services/SampleDataService");
    }

    public void testSuccessEnvelopeShape() {
        JSONObject root = DataServicesAnalyticsPublisher.buildJson(event);

        assertTrue(root.has("serverInfo"));
        assertEquals(1, root.getInt("schemaVersion"));
        assertEquals(Instant.ofEpochMilli(1779164899202L).toString(), root.getString("timestamp"));
        assertTrue(root.has("payload"));

        JSONObject payload = root.getJSONObject("payload");
        assertEquals("DataService", payload.getString("entityType"));
        assertEquals("org.wso2.micro.integrator.dataservices.core.DataService",
                payload.getString("entityClassName"));
        assertEquals(42L, payload.getLong("latency"));
        assertFalse(payload.getBoolean("failure"));
        assertFalse(payload.getBoolean("faultResponse"));
        assertTrue("metadata bag is always present", payload.has("metadata"));
        assertEquals(0, payload.getJSONObject("metadata").length());

        JSONObject details = payload.getJSONObject("dataServiceDetails");
        assertEquals("SampleDataService", details.getString("name"));
        assertEquals("urn:listEmployees", details.getString("operation"));
        assertEquals("DATA_SERVICE",      details.getString("subType"));
    }

    public void testFailureSetsBothBooleansAndStashesErrorInMetadata() {
        event.setStatus(DataServicesAnalyticsConstants.STATUS_FAILURE);
        event.setErrorCode("DATABASE_ERROR");
        event.setErrorMessage("connection refused");

        JSONObject payload = DataServicesAnalyticsPublisher.buildJson(event).getJSONObject("payload");

        assertTrue(payload.getBoolean("failure"));
        assertTrue(payload.getBoolean("faultResponse"));

        JSONObject metadata = payload.getJSONObject("metadata");
        assertEquals("DATABASE_ERROR",      metadata.getString("errorCode"));
        assertEquals("connection refused",  metadata.getString("errorMessage"));
    }

    public void testTimestampLandsAtRoot_notInPayload() {
        JSONObject root = DataServicesAnalyticsPublisher.buildJson(event);
        assertTrue("timestamp must be at root, not under payload",
                root.has("timestamp") && !root.getJSONObject("payload").has("timestamp"));
    }

    public void testOdataSubTypeAppearsUnderDetails() {
        event.setSubType(DataServicesAnalyticsConstants.SUB_TYPE_ODATA);
        JSONObject details = DataServicesAnalyticsPublisher.buildJson(event)
                .getJSONObject("payload")
                .getJSONObject("dataServiceDetails");
        assertEquals("ODATA", details.getString("subType"));
    }

    public void testNullOptionalFieldsAreOmitted() {
        event.setHttpMethod(null);
        event.setHttpUrl(null);
        event.setRemoteHost(null);
        event.setMessageId(null);
        event.setCorrelationId(null);

        JSONObject payload = DataServicesAnalyticsPublisher.buildJson(event).getJSONObject("payload");
        assertFalse(payload.has("httpMethod"));
        assertFalse(payload.has("httpUrl"));
        assertFalse(payload.has("remoteHost"));
        assertFalse(payload.has("messageId"));
        assertFalse(payload.has("correlation_id"));
    }

    public void testCorrelationIdIncludedWhenPresent() {
        event.setCorrelationId("abc-123");
        JSONObject payload = DataServicesAnalyticsPublisher.buildJson(event).getJSONObject("payload");
        assertEquals("abc-123", payload.getString("correlation_id"));
    }

    public void testSuccessEventHasEmptyMetadataBagNotErrors() {
        // Even if errorCode is present on the event, success events must not
        // surface it (matches how the existing publisher behaves).
        event.setErrorCode("WILL_BE_IGNORED");
        event.setErrorMessage("ignored on success");

        JSONObject metadata = DataServicesAnalyticsPublisher.buildJson(event)
                .getJSONObject("payload")
                .getJSONObject("metadata");
        assertEquals(0, metadata.length());
    }
}
