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

public class DataServicesAnalyticsConstants {

    private DataServicesAnalyticsConstants() {}

    // ---- MessageContext property keys ----
    public static final String DS_ANALYTICS_START_TIME = "DS_ANALYTICS_START_TIME";
    public static final String DS_ANALYTICS_PUBLISHED  = "DS_ANALYTICS_PUBLISHED";

    // ---- Explicit overrides for paths where the axis2 context doesn't expose
    //      the right values (OData runs in Synapse mediation; DSS Call Mediator
    //      has the operation name only in a private property). ----
    public static final String DS_ANALYTICS_SERVICE_NAME_OVERRIDE = "DS_ANALYTICS_SERVICE_NAME";
    public static final String DS_ANALYTICS_OPERATION_OVERRIDE    = "DS_ANALYTICS_OPERATION";

    // ---- Property the DSS Call Mediator sets to record the real DS operation.
    //      See DataServiceRequest.AXIS_OPERATION_NAME = "axisOperationName". ----
    public static final String MEDIATOR_AXIS_OPERATION_NAME = "axisOperationName";

    // ---- Axis2 property carrying the inbound request URL. ----
    public static final String TRANSPORT_IN_URL_PROPERTY = "TransportInURL";

    // ---- Entity type discriminator (lands in payload.entityType). PascalCase to
    //      match the WSO2 convention (API, ProxyService, SequenceMediator, ...).
    //      Logstash lowercases this for index routing: "DataService" -> index
    //      "wso2-mi-analytics-dataservice". Do not change to "DATA_SERVICE" — that
    //      would route to "wso2-mi-analytics-data_service" and break dashboards. ----
    public static final String ENTITY_TYPE_DATA_SERVICE       = "DataService";
    public static final String ENTITY_CLASS_NAME_DATA_SERVICE =
            "org.wso2.micro.integrator.dataservices.core.DataService";

    // ---- Sub-type discriminator (lands in payload.dataServiceDetails.subType) ----
    public static final String SUB_TYPE_DATA_SERVICE      = "DATA_SERVICE";
    public static final String SUB_TYPE_ODATA             = "ODATA";
    public static final String SUB_TYPE_DSS_CALL_MEDIATOR = "DSS_CALL_MEDIATOR";

    // ---- Internal status sentinels (set on DataServiceAnalyticsEvent; never serialized) ----
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";

    // ---- JSON field names (envelope root, matches ElasticDataSchema output) ----
    public static final String FIELD_SERVER_INFO    = "serverInfo";
    public static final String FIELD_TIMESTAMP      = "timestamp";
    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    public static final String FIELD_PAYLOAD        = "payload";

    // ---- JSON field names (payload, aligned with API/Proxy/Sequence/Endpoint events) ----
    public static final String FIELD_ENTITY_TYPE          = "entityType";
    public static final String FIELD_ENTITY_CLASS_NAME    = "entityClassName";
    public static final String FIELD_DATA_SERVICE_DETAILS = "dataServiceDetails";
    public static final String FIELD_LATENCY              = "latency";
    public static final String FIELD_FAILURE              = "failure";
    public static final String FIELD_FAULT_RESPONSE       = "faultResponse";
    public static final String FIELD_MESSAGE_ID           = "messageId";
    public static final String FIELD_CORRELATION_ID       = "correlation_id";
    public static final String FIELD_METADATA             = "metadata";
    public static final String FIELD_REMOTE_HOST          = "remoteHost";
    public static final String FIELD_HTTP_METHOD          = "httpMethod";
    public static final String FIELD_HTTP_URL             = "httpUrl";
    public static final String FIELD_HTTP_STATUS_CODE     = "httpStatusCode";

    // ---- JSON field names (dataServiceDetails sub-object) ----
    public static final String FIELD_DETAILS_NAME      = "name";
    public static final String FIELD_DETAILS_OPERATION = "operation";
    public static final String FIELD_DETAILS_SUB_TYPE  = "subType";

    // ---- JSON field names (free-form metadata bag — error info lives here) ----
    public static final String METADATA_ERROR_CODE    = "errorCode";
    public static final String METADATA_ERROR_MESSAGE = "errorMessage";

    // ---- Schema version constant ----
    public static final int SCHEMA_VERSION = 1;

    // ---- Axis2 MessageContext property names we read ----
    public static final String HTTP_METHOD_PROPERTY       = "HTTP_METHOD_OBJECT";
    public static final String TRANSPORT_HEADERS_PROPERTY = "TRANSPORT_HEADERS";
    public static final String REMOTE_ADDR_PROPERTY       = "REMOTE_ADDR";
    public static final String HTTP_RESPONSE_STATUS_CODE  = "HTTP_RESPONSE_STATUS_CODE";
    public static final String CORRELATION_ID_PROPERTY    = "Correlation-ID";
}
