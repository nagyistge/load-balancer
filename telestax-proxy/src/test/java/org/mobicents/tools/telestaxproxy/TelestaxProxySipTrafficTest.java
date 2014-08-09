/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.tools.telestaxproxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.sip.message.Response;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mobicents.tools.sip.balancer.BalancerRunner;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class TelestaxProxySipTrafficTest {

    private static Logger logger = Logger.getLogger(TelestaxProxyMessageTests.class);
    BalancerRunner balancer;
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090);

    private HttpClient restcomm;
    private static final byte[] bytes = new byte[] { 118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
        53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
        48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
        13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
        86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10 };
    private static final String sipBody = new String(bytes);
    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;
    private static SipStackTool tool4;
    
    private SipStack restcommSipStack1;
    private SipPhone restcommPhone1;
    private String restcommContact1 = "sip:14156902867@127.0.0.1:5090";

    private SipStack restcommSipStack2;
    private SipPhone restcommPhone2;
    private String restcommContact2 = "sip:14156902868@127.0.0.1:5091";

    private SipStack restcommSipStack3;
    private SipPhone restcommPhone3;
    private String restcommContact3 = "sip:14156902869@127.0.0.1:5092";

    
    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private String aliceContact = "sip:alice@127.0.0.1:5093";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("TelestaxProxySipTrafficTest1");
        tool2 = new SipStackTool("TelestaxProxySipTrafficTest2");
        tool3 = new SipStackTool("TelestaxProxySipTrafficTest3");
        tool4 = new SipStackTool("TelestaxProxySipTrafficTest4");
    }

    @Before
    public void setup() throws Exception {
        balancer = new org.mobicents.tools.telestaxproxy.sip.balancer.BalancerRunner(); 
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
        properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "2");
        properties.setProperty("host", "127.0.0.1");
        properties.setProperty("internalPort", "5065");
        properties.setProperty("externalPort", "5060");
        properties.setProperty("earlyDialogWorstCase", "true");
        properties.setProperty("algorithmClass", "org.mobicents.tools.telestaxproxy.TelestaxProxyAlgorithmMock");
        properties.setProperty("vi-login","username13");
        properties.setProperty("vi-password","password13");
        properties.setProperty("vi-endpoint", "131313");
        properties.setProperty("vi-uri", "http://127.0.0.1:8090/test");
        properties.setProperty("extraServerNodes", "127.0.0.1:5090,127.0.0.1:5091,127.0.0.1:5092");
        properties.setProperty("performanceTestingMode", "true");
        balancer.start(properties);
        Thread.sleep(1000);
        logger.info("Balancer Started");

        restcommSipStack1 = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5065");
        restcommPhone1 = restcommSipStack1.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5065, restcommContact1);

        restcommSipStack2 = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5065");
        restcommPhone2 = restcommSipStack2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5065, restcommContact2);
        
        restcommSipStack3 = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5065");
        restcommPhone3 = restcommSipStack3.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5065, restcommContact3);
        
        aliceSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5093", "127.0.0.1:5060");
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5060, aliceContact);
    }

    @After
    public void after() throws Exception {
        if (restcommPhone1 != null) {
            restcommPhone1.dispose();
        }
        if (restcommSipStack1 != null) {
            restcommSipStack1.dispose();
        }

        if (aliceSipStack != null) {
            aliceSipStack.dispose();
        }
        if (alicePhone != null) {
            alicePhone.dispose();
        }
        balancer.stop();
    }

    @Test
    public void testAssignDidAndCreateCallToRestcomm1() throws ClientProtocolException, IOException, InterruptedException, ParseException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("username13"))
                .withRequestBody(containing("password13"))
                .withRequestBody(containing("4156902867"))
                .withRequestBody(containing("131313"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(VoipInnovationMessages.getAssignDidResponse(requestId))));

        restcomm = new DefaultHttpClient();

        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("assignDID").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<did>").append("4156902867").append("</did>");
        buffer.append("<endpointgroup>").append("Restcomm_Instance_Id").append("</endpointgroup>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", "AssignDid");
        post.addHeader("OutboundIntf", "127.0.0.1:5090:udp");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<TN>4156902867</TN>"));

        restcommPhone1.setLoopback(true);
        final SipCall restcommCall1 = restcommPhone1.createSipCall();
        restcommCall1.listenForIncomingCall();
        
        final SipCall aliceCall = alicePhone.createSipCall();
        //Need to replace the To header with 127.0.0.1:5092 so the sipUnit will accept the Invite. Also need restcommPhone1.setLoopback(true);
        ArrayList<String> replaceHeaders = new ArrayList<String>();
        replaceHeaders.add("To: "+restcommContact1);
        aliceCall.initiateOutgoingCall(aliceContact, "sip:14156902867@sipbalancer.com", null, sipBody, "application", "sdp", null, replaceHeaders);
        assertLastOperationSuccess(aliceCall);
        
        assertTrue(restcommCall1.waitForIncomingCall(5000));
        assertTrue(restcommCall1.sendIncomingCallResponse(180, "Restcomm-Ringing", 3600));
        assertTrue(aliceCall.waitOutgoingCallResponse(5 * 1000));    
        assertTrue(aliceCall.getLastReceivedResponse().getStatusCode() == Response.RINGING); 

        assertTrue(restcommCall1.sendIncomingCallResponse(200, "Restcomm-OK", 3600, null, null, sipBody));
        
        assertTrue(aliceCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, aliceCall.getLastReceivedResponse().getStatusCode());
        assertTrue(aliceCall.sendInviteOkAck());
        assertTrue(!(aliceCall.getLastReceivedResponse().getStatusCode() >= 400));
        
        Thread.sleep(2000);
        
        assertTrue(aliceCall.disconnect());
        assertTrue(restcommCall1.waitForDisconnect(2000));
        assertTrue(restcommCall1.respondToDisconnect());
    }
    
    @Test
    public void testAssignDidAndCreateCallToRestcomm3() throws ClientProtocolException, IOException, InterruptedException, ParseException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("username13"))
                .withRequestBody(containing("password13"))
                .withRequestBody(containing("4156902869"))
                .withRequestBody(containing("131313"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(VoipInnovationMessages.getAssignDidResponse(requestId, "4156902869"))));

        restcomm = new DefaultHttpClient();

        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("assignDID").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<did>").append("4156902869").append("</did>");
        buffer.append("<endpointgroup>").append("Restcomm_Instance_Id").append("</endpointgroup>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", "AssignDid");
        post.addHeader("OutboundIntf", "127.0.0.1:5092:udp");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<TN>4156902869</TN>"));

        restcommPhone1.setLoopback(true);
        final SipCall restcommCall1 = restcommPhone1.createSipCall();
        restcommCall1.listenForIncomingCall();


        restcommPhone2.setLoopback(true);
        final SipCall restcommCall2 = restcommPhone2.createSipCall();
        restcommCall2.listenForIncomingCall();
        
        restcommPhone3.setLoopback(true);
        final SipCall restcommCall3 = restcommPhone3.createSipCall();
        restcommCall3.listenForIncomingCall();
        
        final SipCall aliceCall = alicePhone.createSipCall();
        //Need to replace the To header with 127.0.0.1:5092 so the sipUnit will accept the Invite. Also need restcommPhone1.setLoopback(true);
        ArrayList<String> replaceHeaders = new ArrayList<String>();
        replaceHeaders.add("To: "+restcommContact3);
        aliceCall.initiateOutgoingCall(aliceContact, "sip:14156902869@sipbalancer.com", null, sipBody, "application", "sdp", null, replaceHeaders);
        assertLastOperationSuccess(aliceCall);
        
        assertFalse(restcommCall1.waitForIncomingCall(2000));
        assertFalse(restcommCall2.waitForIncomingCall(2000));
        assertTrue(restcommCall3.waitForIncomingCall(2000));
        assertTrue(restcommCall3.sendIncomingCallResponse(180, "Restcomm-Ringing", 3600));
        assertTrue(aliceCall.waitOutgoingCallResponse(5 * 1000));    
        assertTrue(aliceCall.getLastReceivedResponse().getStatusCode() == Response.RINGING); 

        assertTrue(restcommCall3.sendIncomingCallResponse(200, "Restcomm-OK", 3600, null, null, sipBody));
        
        assertTrue(aliceCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, aliceCall.getLastReceivedResponse().getStatusCode());
        assertTrue(aliceCall.sendInviteOkAck());
        assertTrue(!(aliceCall.getLastReceivedResponse().getStatusCode() >= 400));
        
        Thread.sleep(2000);
        
        assertTrue(aliceCall.disconnect());
        assertTrue(restcommCall3.waitForDisconnect(2000));
        assertTrue(restcommCall3.respondToDisconnect());
    }

    private String header() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<header><sender>");
        buffer.append("<login>restcomm</login>");
        buffer.append("<password>restcomm</password>");
        buffer.append("</sender></header>");
        return buffer.toString();
    }
    
    @Test
    public void testCreateCallToUnknownDid() throws ClientProtocolException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("username13"))
                .withRequestBody(containing("password13"))
                .withRequestBody(containing("4156902867"))
                .withRequestBody(containing("131313"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(VoipInnovationMessages.getAssignDidResponse(requestId))));

        restcomm = new DefaultHttpClient();

        final StringBuilder buffer = new StringBuilder();
        buffer.append("<request id=\""+requestId+"\">");
        buffer.append(header());
        buffer.append("<body>");
        buffer.append("<requesttype>").append("assignDID").append("</requesttype>");
        buffer.append("<item>");
        buffer.append("<did>").append("4156902867").append("</did>");
        buffer.append("<endpointgroup>").append("Restcomm_Instance_Id").append("</endpointgroup>");
        buffer.append("</item>");
        buffer.append("</body>");
        buffer.append("</request>");
        final String body = buffer.toString();

        HttpPost post = new HttpPost("http://127.0.0.1:2080");

        post.addHeader("TelestaxProxy", "true");
        post.addHeader("RequestType", "AssignDid");
        post.addHeader("OutboundIntf", "127.0.0.1:5090:udp");

        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("apidata", body));
        post.setEntity(new UrlEncodedFormEntity(parameters));

        final HttpResponse response = restcomm.execute(post);
        assertTrue(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
        String responseContent = EntityUtils.toString(response.getEntity());
        assertTrue(responseContent.contains("<statuscode>100</statuscode>"));
        assertTrue(responseContent.contains("<response id=\""+requestId+"\">"));
        assertTrue(responseContent.contains("<TN>4156902867</TN>"));
        
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.initiateOutgoingCall(aliceContact, "sip:1313131313@sipbalancer.com", null, sipBody, "application", "sdp", null, null);
        assertLastOperationSuccess(aliceCall);

        assertTrue(aliceCall.waitOutgoingCallResponse(5 * 1000));    
        assertTrue(aliceCall.getLastReceivedResponse().getStatusCode() == Response.SERVER_INTERNAL_ERROR); 
    }
    

}
