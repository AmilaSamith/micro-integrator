/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.ei.scenario.test;

import org.apache.http.HttpResponse;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.esb.scenario.test.common.Request;
import org.wso2.carbon.esb.scenario.test.common.ScenarioConstants;
import org.wso2.carbon.esb.scenario.test.common.ScenarioTestBase;
import org.wso2.carbon.esb.scenario.test.common.StringUtil;
import org.wso2.carbon.esb.scenario.test.common.http.HttpConstants;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tests a load balance endpoint group with 2 endpoints.
 */
public class LoadBalanceEndpointsWithLoadBalanceGroupTest extends ScenarioTestBase {

    private String apiInvocationUrl;

    private final String[] responses = new String[]{"{\"name\": \"John\",\"age\": 30,\"server\": 1,\"cars\": "
                                                    + "{\"car1\": \"Ford\",\"car2\": \"BMW\",\"car3\": \"Fiat\"}}",
                                                    "{\"name\": \"John\",\"age\": 30,\"server\": 2,\"cars\": "
                                                    + "{\"car1\": \"Ford\",\"car2\": \"BMW\",\"car3\": \"Fiat\"}}"};

    private final String LOAD_BALANCE_MESSAGE_HEADER = "loadBalanceMsg";

    @BeforeClass()
    public void init() throws Exception {
        super.init();
        String apiName = "5_3_1_1_API_load_balance_group_test";
        apiInvocationUrl = getApiInvocationURLHttp(apiName);
    }

    /**
     * Tests a load balance endpoint group with two endpoints and default configurations.
     *
     * @throws Exception if any error occurs during the execution of the test
     */
    @Test(description = "5.3.1.1-Test load balance endpoint group with two endpoints and default configurations")
    public void testLoadBalanceEndpointGroup() throws Exception {

        for (int i = 0; i < 2; i++) {
            SimpleHttpClient httpClient = new SimpleHttpClient();

            Map<String, String> headers = new HashMap<>();
            headers.put(ScenarioConstants.MESSAGE_ID, LOAD_BALANCE_MESSAGE_HEADER);
            HttpResponse httpResponse = httpClient.doPost(apiInvocationUrl, headers,
                                                          ScenarioConstants.BASIC_JSON_MESSAGE,
                                                          HttpConstants.MEDIA_TYPE_APPLICATION_JSON);
            String responsePayload = httpClient.getResponsePayload(httpResponse);
            Assert.assertEquals(httpResponse.getStatusLine().getStatusCode(), 200,
                                "Load balance test failed, incorrect response code received");
            Assert.assertEquals(StringUtil.trimTabsSpaceNewLinesBetweenJsonTags(responsePayload),
                                StringUtil.trimTabsSpaceNewLinesBetweenJsonTags(responses[i]),
                                "Actual Response and Expected Response mismatch!");
        }
    }

}
