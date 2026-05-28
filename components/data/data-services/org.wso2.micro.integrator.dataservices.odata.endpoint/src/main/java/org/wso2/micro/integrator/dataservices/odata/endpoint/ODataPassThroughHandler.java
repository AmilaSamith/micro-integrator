/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.micro.integrator.dataservices.odata.endpoint;

import java.io.IOException;
import javax.ws.rs.core.MediaType;
import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.AbstractSynapseHandler;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.util.RelayUtils;
import org.wso2.micro.integrator.dataservices.core.analytics.DataServicesAnalyticsCollector;
import org.wso2.micro.integrator.dataservices.core.analytics.DataServicesAnalyticsConstants;
import org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingCollector;

import javax.xml.stream.XMLStreamException;

import static org.wso2.micro.integrator.dataservices.core.opentelemetry.DataServicesTracingConstants.DATA_SERVICE_INDEX;

public class ODataPassThroughHandler extends AbstractSynapseHandler {
    private static final Log LOG = LogFactory.getLog(ODataPassThroughHandler.class);

    private static final String IS_ODATA_SERVICE = "IsODataService";
    private static final String TRANSPORT_IN_URL = "TransportInURL";

    @Override
    public boolean handleRequestInFlow(MessageContext messageContext) {
        try {
            org.apache.axis2.context.MessageContext axis2MessageContext =
                    ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            Object isODataService = axis2MessageContext.getProperty(IS_ODATA_SERVICE);
            // In this if block we are skipping proxy services, inbound related message contexts & api.
            if (axis2MessageContext.getProperty(TRANSPORT_IN_URL) != null && isODataService != null) {
                // Marking the message to skip the main sequence in Synapse Axis2 Environment.
                messageContext.setProperty(SynapseConstants.SKIP_MAIN_SEQUENCE, Boolean.TRUE);

                DataServicesTracingCollector.reportOdataEntryEvent(axis2MessageContext);
                populateODataAnalyticsContext(axis2MessageContext);
                DataServicesAnalyticsCollector.reportEntryEvent(axis2MessageContext);

                RelayUtils.buildMessage(axis2MessageContext);
                ODataServletRequest request = new ODataServletRequest(axis2MessageContext);
                ODataServletResponse response = new ODataServletResponse(axis2MessageContext);
                synchronized (this) {
                    ODataEndpoint.process(request, response);
                    streamResponseBack(response, messageContext, axis2MessageContext);
                }
                DataServicesTracingCollector.closeOdataEntryEvent(axis2MessageContext,
                        axis2MessageContext.getEnvelope());
                DataServicesAnalyticsCollector.closeEntryEventWithSubType(
                        axis2MessageContext,
                        axis2MessageContext.getEnvelope() != null
                                ? axis2MessageContext.getEnvelope().getBody().getFirstElement() : null,
                        DataServicesAnalyticsConstants.SUB_TYPE_ODATA);
            }
            return true;
        } catch (Exception e) {
            DataServicesTracingCollector.closeFlowForcefully(
                    ((Axis2MessageContext) messageContext).getAxis2MessageContext(), DATA_SERVICE_INDEX, e);
            DataServicesAnalyticsCollector.closeFlowForcefullyWithSubType(
                    ((Axis2MessageContext) messageContext).getAxis2MessageContext(), e,
                    DataServicesAnalyticsConstants.SUB_TYPE_ODATA);
            this.handleException("Error occurred in integrator handler.", e, messageContext);
            return true;
        }
    }

    /**
     * This method starts streaming the response from the server to the client.
     *
     * @param response       OData Servlet response.
     * @param messageContext Message context.
     * @throws XMLStreamException if unexpected processing errors occurred while building the message.
     * @throws IOException        if any interrupted I/O operations occurred while building the message.
     */
    private void streamResponseBack(ODataServletResponse response, MessageContext messageContext,
                                    org.apache.axis2.context.MessageContext axis2MessageContext) throws IOException {
        axis2MessageContext.setProperty(Constants.Configuration.MESSAGE_TYPE, MediaType.TEXT_PLAIN);
        axis2MessageContext.removeProperty(PassThroughConstants.NO_ENTITY_BODY);
        messageContext.setTo(null);
        messageContext.setResponse(true);
        ODataAxisEngine oDataAxisEngine = new ODataAxisEngine();
        oDataAxisEngine.stream(axis2MessageContext, response);
    }

    private static final String ODATA_PATH_PREFIX = "/odata/";

    /**
     * Parses the OData inbound URL and stamps the DS analytics overrides on
     * the message context. URL shape: {@code /odata/<dsName>/<config>/<Entity>(key)}.
     * Without these overrides the analytics event reports the service name as
     * Synapse's internal {@code __SynapseService} and operation as {@code mediate}.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Avoids {@code String.split("/")} — that allocates an array per request
     *       (this is the OData hot path) and uses regex.</li>
     *   <li>Handles URLs that include scheme/host (e.g. behind a reverse proxy
     *       where {@code TransportInURL} may contain {@code http://host/odata/...})
     *       by routing through {@link java.net.URI#getPath()}.</li>
     * </ul>
     */
    private void populateODataAnalyticsContext(org.apache.axis2.context.MessageContext axis2MessageContext) {
        Object urlObj = axis2MessageContext.getProperty(TRANSPORT_IN_URL);
        if (urlObj == null) return;
        String path = toPath(urlObj.toString());
        if (path == null || !path.regionMatches(true, 0, ODATA_PATH_PREFIX, 0, ODATA_PATH_PREFIX.length())) {
            return;
        }
        int dsStart = ODATA_PATH_PREFIX.length();
        int dsEnd = path.indexOf('/', dsStart);
        if (dsEnd <= dsStart) return;
        String dsName = path.substring(dsStart, dsEnd);
        if (!dsName.isEmpty()) {
            axis2MessageContext.setProperty(
                    DataServicesAnalyticsConstants.DS_ANALYTICS_SERVICE_NAME_OVERRIDE, dsName);
        }
        // Operation = the most-specific segment of the URL after /odata/<dsName>/.
        //   /odata/ds/cfg                       → "cfg"        (service document)
        //   /odata/ds/cfg/Entity                → "Entity"
        //   /odata/ds/cfg/Entity(1)             → "Entity"     (key stripped)
        //   /odata/ds/cfg/$metadata             → "$metadata"
        //   /odata/ds/cfg/Customers(1)/Orders   → "Orders"     (deepest segment wins)
        //
        // We MUST set this even when no entity is present — otherwise the
        // resolver falls through to firstBodyElementName, which axis2 sets to
        // "text" for empty / plain REST bodies (producing junk operation names
        // like "text" for service-document URLs).
        String operation = extractODataOperation(path, dsEnd);
        if (operation != null) {
            axis2MessageContext.setProperty(
                    DataServicesAnalyticsConstants.DS_ANALYTICS_OPERATION_OVERRIDE, operation);
        }
    }

    /**
     * Picks the deepest meaningful URL segment after {@code /odata/<dsName>/} as
     * the OData operation name. Strips query strings, trailing slashes, and
     * entity keys (everything from the first {@code '('}). Returns {@code null}
     * if the URL is just {@code /odata/<dsName>} or {@code /odata/<dsName>/}
     * (nothing more specific than the service name itself).
     */
    private static String extractODataOperation(String path, int dsEnd) {
        int end = path.indexOf('?', dsEnd);
        if (end < 0) end = path.length();
        while (end > dsEnd + 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        if (end <= dsEnd + 1) return null;
        int lastSlash = path.lastIndexOf('/', end - 1);
        int segStart = (lastSlash <= dsEnd) ? dsEnd + 1 : lastSlash + 1;
        int segEnd = end;
        int paren = path.indexOf('(', segStart);
        if (paren > 0 && paren < segEnd) segEnd = paren;
        if (segStart >= segEnd) return null;
        return path.substring(segStart, segEnd);
    }

    /**
     * Returns the path portion of {@code url}. Handles both plain paths
     * ({@code /odata/...}) and absolute URLs ({@code http://host/odata/...}).
     * Returns {@code null} for malformed input — caller treats that as "no
     * analytics overrides".
     */
    private static String toPath(String url) {
        if (url.isEmpty()) return null;
        if (url.charAt(0) == '/') return url;
        try {
            return new java.net.URI(url).getPath();
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    private void handleException(String msg, Exception e, MessageContext msgContext) {
        LOG.error(msg, e);
        if (msgContext.getServiceLog() != null) {
            msgContext.getServiceLog().error(msg, e);
        }
        throw new SynapseException(msg, e);
    }

    @Override
    public boolean handleRequestOutFlow(MessageContext messageContext) {
        return true;
    }

    @Override
    public boolean handleResponseInFlow(MessageContext messageContext) {
        return true;
    }

    @Override
    public boolean handleResponseOutFlow(MessageContext messageContext) {
        return true;
    }

}
