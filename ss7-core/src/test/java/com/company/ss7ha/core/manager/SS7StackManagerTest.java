package com.company.ss7ha.core.manager;

import com.company.ss7ha.core.config.SS7Configuration;
import com.company.ss7ha.core.stack.SS7Stack;
import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.restcomm.protocols.sctp.SctpManagementImpl;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SS7StackManagerTest {

    // Dummy interface to mock the Association object for reflection
    public interface MockAssociation {
        boolean isConnected();
    }

    @Mock
    private SS7Stack mockStack;

    @Mock
    private SS7NatsPublisher mockPublisher;

    @Mock
    private SctpManagementImpl mockSctpManagement;

    @Mock
    private MockAssociation mockAssociation;

    private SS7StackManager stackManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        stackManager = SS7StackManager.getInstance();
        stackManager.setPublisher(mockPublisher);

        // Inject mockStack into singleton via Reflection
        Field stackField = SS7StackManager.class.getDeclaredField("ss7Stack");
        stackField.setAccessible(true);
        stackField.set(stackManager, mockStack);
        
        // Reset alarm flags via Reflection
        Field sctpAlarmField = SS7StackManager.class.getDeclaredField("sctpAlarmActive");
        sctpAlarmField.setAccessible(true);
        sctpAlarmField.set(stackManager, false);
        
        Field dialogAlarmField = SS7StackManager.class.getDeclaredField("dialogAlarmActive");
        dialogAlarmField.setAccessible(true);
        dialogAlarmField.set(stackManager, false);
    }

    @Test
    public void testStartPublishesInfoEvent() throws Exception {
        // Arrange - No specific stubbing needed for isStarted() as it's not called directly in stackManager.start()
        // Act
        stackManager.start();

        // Assert
        verify(mockStack).start();
        verify(mockPublisher).publishOpsEvent(
            eq("EVT-INFO-001"), 
            eq("INFO"), 
            any(String.class), 
            any(Map.class)
        );
    }

    @Test
    public void testStopPublishesInfoEvent() {
        // Act
        stackManager.stop();

        // Assert
        verify(mockStack).stop();
        verify(mockPublisher).publishOpsEvent(
            eq("EVT-INFO-002"), 
            eq("INFO"), 
            any(String.class), 
            any(Map.class)
        );
    }
    
    @Test
    public void testMonitorHealthPublishesSctpDownEvent() throws Exception {
        // Arrange
        when(mockStack.isStarted()).thenReturn(true);
        when(mockStack.getSctpManagement()).thenReturn(mockSctpManagement);
        
        // Return empty associations map -> SCTP DOWN
        when(mockSctpManagement.getAssociations()).thenReturn(new HashMap());

        // Access private monitorHealth method
        java.lang.reflect.Method monitorMethod = SS7StackManager.class.getDeclaredMethod("monitorHealth");
        monitorMethod.setAccessible(true);

        // Act
        monitorMethod.invoke(stackManager);

        // Assert
        verify(mockPublisher).publishOpsEvent(
            eq("EVT-CRIT-002"), 
            eq("CRITICAL"), 
            contains("SCTP Link Down"), 
            any(Map.class)
        );
    }
    
    @Test
    public void testMonitorHealthPublishesSctpRecoveredEvent() throws Exception {
        // Arrange: Set alarm active first
        Field sctpAlarmField = SS7StackManager.class.getDeclaredField("sctpAlarmActive");
        sctpAlarmField.setAccessible(true);
        sctpAlarmField.set(stackManager, true);
        
        when(mockStack.isStarted()).thenReturn(true);
        when(mockStack.getSctpManagement()).thenReturn(mockSctpManagement);
        
        // Return one connected association -> SCTP UP
        Map<String, Object> assocs = new HashMap<>();
        when(mockAssociation.isConnected()).thenReturn(true);
        assocs.put("assoc1", mockAssociation);
        when(mockSctpManagement.getAssociations()).thenReturn((Map)assocs);

        // Access private monitorHealth method
        java.lang.reflect.Method monitorMethod = SS7StackManager.class.getDeclaredMethod("monitorHealth");
        monitorMethod.setAccessible(true);

        // Act
        monitorMethod.invoke(stackManager);

        // Assert
        verify(mockPublisher).publishOpsEvent(
            eq("EVT-INFO-003"), 
            eq("INFO"), 
            contains("SCTP Link Recovered"), 
            any(Map.class)
        );
    }
}
