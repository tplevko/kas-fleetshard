package org.bf2.operator.operands;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.bf2.operator.resources.v1alpha1.ManagedKafka;
import org.bf2.operator.resources.v1alpha1.ManagedKafkaCondition.Reason;
import org.bf2.operator.resources.v1alpha1.ManagedKafkaCondition.Status;
import org.bf2.operator.utils.ManagedKafkaUtils;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@QuarkusTest
public class KafkaInstanceTest {

    private static final ManagedKafka DUMMY_MANAGED_KAFKA = ManagedKafkaUtils.dummyManagedKafka("x");

    @InjectMock
    KafkaCluster kafkaCluster;

    @InjectMock
    Canary canary;

    @InjectMock
    AdminServer adminServer;

    @Inject
    KafkaInstance kafkaInstance;

    @Test
    void statusInstallingTrumpsError() {
        ManagedKafka managedKafka = DUMMY_MANAGED_KAFKA;

        when(kafkaCluster.getReadiness(managedKafka)).thenReturn(new OperandReadiness(Status.True, Reason.StrimziUpdating, null));
        when(canary.getReadiness(managedKafka)).thenReturn(new OperandReadiness(Status.False, Reason.Error, "I'm not well"));
        when(adminServer.getReadiness(managedKafka)).thenReturn(new OperandReadiness(Status.False, Reason.Installing, "I'm installing"));

        OperandReadiness readiness = kafkaInstance.getReadiness(managedKafka);
        assertEquals(Status.False, readiness.getStatus());
        assertEquals(Reason.Installing, readiness.getReason());
        assertEquals("I'm not well; I'm installing", readiness.getMessage());
    }

    @Test
    void statusErrorTrumpsReady() {

        when(kafkaCluster.getReadiness(DUMMY_MANAGED_KAFKA)).thenReturn(new OperandReadiness(Status.False, Reason.Error, "I'm not well"));
        when(canary.getReadiness(DUMMY_MANAGED_KAFKA)).thenReturn(new OperandReadiness(Status.True, null, null));
        when(adminServer.getReadiness(DUMMY_MANAGED_KAFKA)).thenReturn(new OperandReadiness(Status.False, Reason.Error, "I'm not well"));

        OperandReadiness readiness = kafkaInstance.getReadiness(DUMMY_MANAGED_KAFKA);
        assertEquals(Status.False, readiness.getStatus());
        assertEquals(Reason.Error, readiness.getReason());
        assertEquals("I'm not well; I'm not well", readiness.getMessage());
    }

    @Test
    void statusReadyWithReason() {

        when(kafkaCluster.getReadiness(DUMMY_MANAGED_KAFKA)).thenReturn(new OperandReadiness(Status.True, Reason.StrimziUpdating, null));

        OperandReadiness readiness = kafkaInstance.getReadiness(DUMMY_MANAGED_KAFKA);
        assertEquals(Status.True, readiness.getStatus());
        assertEquals(Reason.StrimziUpdating, readiness.getReason());
        assertEquals("", readiness.getMessage());
    }

    @Test
    void statusUnknownWithReason() {

        when(kafkaCluster.getReadiness(DUMMY_MANAGED_KAFKA)).thenReturn(new OperandReadiness(Status.True, Reason.StrimziUpdating, null));
        when(adminServer.getReadiness(DUMMY_MANAGED_KAFKA)).thenReturn(new OperandReadiness(Status.Unknown, null, "I don't know"));

        OperandReadiness readiness = kafkaInstance.getReadiness(DUMMY_MANAGED_KAFKA);
        assertEquals(Status.Unknown, readiness.getStatus());
        assertEquals(Reason.StrimziUpdating, readiness.getReason());
        assertEquals("I don't know", readiness.getMessage());
    }

    @Test
    void deleteOrderCanaryDeleteBeforeKafkaCluster() {
        Context context = Mockito.mock(Context.class);

        kafkaInstance.delete(DUMMY_MANAGED_KAFKA, context);

        InOrder inOrder = inOrder(canary, kafkaCluster);
        inOrder.verify(canary).delete(DUMMY_MANAGED_KAFKA, context);
        inOrder.verify(kafkaCluster).delete(DUMMY_MANAGED_KAFKA, context);
    }
}
