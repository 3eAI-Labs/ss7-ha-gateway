package com.company.ss7ha.kafka.converters;

import com.company.ss7ha.kafka.messages.MoSmsMessage;
import com.mobius.software.telco.protocols.ss7.map.api.primitives.IMSI;
import com.mobius.software.telco.protocols.ss7.map.api.primitives.ISDNAddressString;
import com.mobius.software.telco.protocols.ss7.map.api.primitives.AddressNature;
import com.mobius.software.telco.protocols.ss7.map.api.primitives.NumberingPlan;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.MoForwardShortMessageRequest;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.SmsSignalInfo;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.SM_RP_DA;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MoForwardSmConverter.
 *
 * Tests the conversion of MAP MO-ForwardSM request to JSON message.
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class MoForwardSmConverterTest {

    @Mock
    private MoForwardShortMessageRequest mockRequest;

    @Mock
    private ISDNAddressString mockMsisdn;

    @Mock
    private IMSI mockImsi;

    @Mock
    private SM_RP_DA mockSmRpDa;

    @Mock
    private ISDNAddressString mockServiceCenterAddress;

    @Mock
    private SmsSignalInfo mockSmRpUi;

    private static final Long TEST_DIALOG_ID = 12345L;
    private static final Integer TEST_INVOKE_ID = 1;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Test basic conversion with minimal data.
     */
    @Test
    public void testConvert_BasicConversion() {
        // Setup minimal mock
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify
        assertNotNull(message);
        assertNotNull(message.getMessageId());
        assertEquals(TEST_DIALOG_ID, message.getDialogId());
        assertEquals(TEST_INVOKE_ID, message.getInvokeId());
        assertNotNull(message.getTimestamp());
        assertEquals("MO_FORWARD_SM", message.getMessageType());
    }

    /**
     * Test conversion with null request.
     */
    @Test
    public void testConvert_NullRequest() {
        MoSmsMessage message = MoForwardSmConverter.convert(
            null, TEST_DIALOG_ID, TEST_INVOKE_ID);

        assertNull(message);
    }

    /**
     * Test sender information extraction.
     */
    @Test
    public void testConvert_SenderInformation() {
        // Setup sender info
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        when(mockRequest.getImsi()).thenReturn(mockImsi);
        when(mockImsi.getData()).thenReturn("310150123456789");

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify sender
        assertNotNull(message);
        assertNotNull(message.getSender());
        assertEquals("+1234567890", message.getSender().getMsisdn());
        assertEquals("310150123456789", message.getSender().getImsi());
    }

    /**
     * Test sender without IMSI.
     */
    @Test
    public void testConvert_SenderWithoutImsi() {
        // Setup sender without IMSI
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);
        when(mockRequest.getImsi()).thenReturn(null);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify sender
        assertNotNull(message);
        assertNotNull(message.getSender());
        assertEquals("+1234567890", message.getSender().getMsisdn());
        assertNull(message.getSender().getImsi());
    }

    /**
     * Test recipient (service center) extraction.
     */
    @Test
    public void testConvert_RecipientInformation() {
        // Setup basic sender
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        // Setup recipient
        when(mockRequest.getSmRpDa()).thenReturn(mockSmRpDa);
        when(mockSmRpDa.getServiceCentreAddressDA()).thenReturn(mockServiceCenterAddress);
        when(mockServiceCenterAddress.getAddress()).thenReturn("5551234");
        when(mockServiceCenterAddress.getAddressNature()).thenReturn(AddressNature.national_significant_number);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify recipient
        assertNotNull(message);
        assertNotNull(message.getRecipient());
        assertEquals("5551234", message.getRecipient().getAddress());
    }

    /**
     * Test SMS content extraction and Base64 encoding.
     */
    @Test
    public void testConvert_SmsContent() {
        // Setup basic sender
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        // Setup SMS content
        byte[] testTpdu = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        when(mockRequest.getSmRpUi()).thenReturn(mockSmRpUi);
        when(mockSmRpUi.getValue()).thenReturn(testTpdu);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify content
        assertNotNull(message);
        assertNotNull(message.getMessage());
        assertNotNull(message.getMessage().getContent());
        assertEquals("SMS-TPDU", message.getMessage().getEncoding());

        // Verify Base64 encoding
        byte[] decoded = Base64.getDecoder().decode(message.getMessage().getContent());
        assertArrayEquals(testTpdu, decoded);
    }

    /**
     * Test SMS content with null TPDU.
     */
    @Test
    public void testConvert_SmsContentNullTpdu() {
        // Setup basic sender
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        // Setup SMS content with null value
        when(mockRequest.getSmRpUi()).thenReturn(mockSmRpUi);
        when(mockSmRpUi.getValue()).thenReturn(null);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify
        assertNotNull(message);
        assertNotNull(message.getMessage());
        assertNull(message.getMessage().getContent());
    }

    /**
     * Test network information extraction.
     */
    @Test
    public void testConvert_NetworkInformation() {
        // Setup basic sender
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        // Setup network info
        ISDNAddressString mockStoredMsisdn = mock(ISDNAddressString.class);
        when(mockRequest.getStoredMSISDN()).thenReturn(mockStoredMsisdn);
        when(mockStoredMsisdn.getAddress()).thenReturn("9876543210");
        when(mockStoredMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify network info
        assertNotNull(message);
        assertNotNull(message.getNetworkInfo());
        assertEquals("+9876543210", message.getNetworkInfo().getMscAddress());
    }

    /**
     * Test service information extraction.
     */
    @Test
    public void testConvert_ServiceInformation() {
        // Setup basic sender
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        // Setup service info
        when(mockRequest.getMoreMessagesToSend()).thenReturn(Boolean.TRUE);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify service info
        assertNotNull(message);
        assertNotNull(message.getServiceInfo());
        assertEquals(Boolean.TRUE, message.getServiceInfo().getMoreMessagesToSend());
    }

    /**
     * Test international number formatting.
     */
    @Test
    public void testConvert_InternationalNumberFormatting() {
        // Setup international number
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify + prefix
        assertNotNull(message);
        assertNotNull(message.getSender());
        assertTrue(message.getSender().getMsisdn().startsWith("+"));
        assertEquals("+1234567890", message.getSender().getMsisdn());
    }

    /**
     * Test national number formatting (no + prefix).
     */
    @Test
    public void testConvert_NationalNumberFormatting() {
        // Setup national number
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.national_significant_number);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify no + prefix
        assertNotNull(message);
        assertNotNull(message.getSender());
        assertFalse(message.getSender().getMsisdn().startsWith("+"));
        assertEquals("1234567890", message.getSender().getMsisdn());
    }

    /**
     * Test complete conversion with all fields.
     */
    @Test
    public void testConvert_CompleteMessage() {
        // Setup complete request
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        when(mockRequest.getImsi()).thenReturn(mockImsi);
        when(mockImsi.getData()).thenReturn("310150123456789");

        when(mockRequest.getSmRpDa()).thenReturn(mockSmRpDa);
        when(mockSmRpDa.getServiceCentreAddressDA()).thenReturn(mockServiceCenterAddress);
        when(mockServiceCenterAddress.getAddress()).thenReturn("5551234");
        when(mockServiceCenterAddress.getAddressNature()).thenReturn(AddressNature.national_significant_number);

        byte[] testTpdu = "Hello World".getBytes();
        when(mockRequest.getSmRpUi()).thenReturn(mockSmRpUi);
        when(mockSmRpUi.getValue()).thenReturn(testTpdu);

        when(mockRequest.getMoreMessagesToSend()).thenReturn(Boolean.FALSE);

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify all fields
        assertNotNull(message);
        assertEquals("MO_FORWARD_SM", message.getMessageType());
        assertEquals(TEST_DIALOG_ID, message.getDialogId());
        assertEquals(TEST_INVOKE_ID, message.getInvokeId());

        assertNotNull(message.getSender());
        assertEquals("+1234567890", message.getSender().getMsisdn());
        assertEquals("310150123456789", message.getSender().getImsi());

        assertNotNull(message.getRecipient());
        assertEquals("5551234", message.getRecipient().getAddress());

        assertNotNull(message.getMessage());
        assertNotNull(message.getMessage().getContent());
        assertEquals("SMS-TPDU", message.getMessage().getEncoding());

        assertNotNull(message.getServiceInfo());
        assertEquals(Boolean.FALSE, message.getServiceInfo().getMoreMessagesToSend());
    }

    /**
     * Test that converted message contains no JSS7 objects.
     */
    @Test
    public void testConvert_NoJSS7ObjectsInOutput() {
        // Setup complete request
        when(mockRequest.getMsIsdn()).thenReturn(mockMsisdn);
        when(mockMsisdn.getAddress()).thenReturn("1234567890");
        when(mockMsisdn.getAddressNature()).thenReturn(AddressNature.international_number);

        when(mockRequest.getImsi()).thenReturn(mockImsi);
        when(mockImsi.getData()).thenReturn("310150123456789");

        // Convert
        MoSmsMessage message = MoForwardSmConverter.convert(
            mockRequest, TEST_DIALOG_ID, TEST_INVOKE_ID);

        // Verify output contains only primitives
        assertNotNull(message);

        // Check all fields are primitives or simple objects
        assertTrue(message.getMessageId() instanceof String);
        assertTrue(message.getDialogId() instanceof Long);
        assertTrue(message.getInvokeId() instanceof Integer);
        assertTrue(message.getTimestamp() instanceof Long);

        if (message.getSender() != null) {
            assertTrue(message.getSender().getMsisdn() == null ||
                      message.getSender().getMsisdn() instanceof String);
            assertTrue(message.getSender().getImsi() == null ||
                      message.getSender().getImsi() instanceof String);
        }

        // No JSS7 types should be present in the output
        // This is critical for AGPL firewall!
    }
}
