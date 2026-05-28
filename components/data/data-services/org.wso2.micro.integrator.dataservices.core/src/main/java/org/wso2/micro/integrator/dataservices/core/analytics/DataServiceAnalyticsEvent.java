/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

/** Plain data holder for one analytics event. */
public class DataServiceAnalyticsEvent {

    private String serviceName;
    private String operationName;
    private long latency;
    private String status;
    private String messageId;
    private String correlationId;
    private long timestamp;
    private String remoteHost;
    private String errorCode;
    private String errorMessage;
    private String faultDetail;
    private String httpMethod;
    private String httpUrl;
    private String subType;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String v) {
        this.serviceName = v;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String v) {
        this.operationName = v;
    }

    public long getLatency() {
        return latency;
    }

    public void setLatency(long v) {
        this.latency = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String v) {
        this.messageId = v;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String v) {
        this.correlationId = v;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long v) {
        this.timestamp = v;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String v) {
        this.remoteHost = v;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String v) {
        this.errorCode = v;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String v) {
        this.errorMessage = v;
    }

    public String getFaultDetail() {
        return faultDetail;
    }

    public void setFaultDetail(String v) {
        this.faultDetail = v;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String v) {
        this.httpMethod = v;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(String v) {
        this.httpUrl = v;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String v) {
        this.subType = v;
    }
}
