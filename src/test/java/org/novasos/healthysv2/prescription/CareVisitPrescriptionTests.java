package org.novasos.healthysv2.prescription;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.prescription.api.CareVisitPrescriptionDtos.*;
import org.novasos.healthysv2.shared.api.error.*;

class CareVisitPrescriptionTests {
    @Test void finalizedPrescriptionRequiresCompleteMedicationLines() {
        assertThatThrownBy(()->CareVisitPrescriptionService.validateItems(List.of(),true)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->CareVisitPrescriptionService.validateItems(List.of(new Item("Drug","500mg","oral","","3 days",null)),true)).isInstanceOf(BusinessRuleException.class);
        assertThatCode(()->CareVisitPrescriptionService.validateItems(List.of(new Item("Drug","500mg","oral","twice daily","3 days",null)),true)).doesNotThrowAnyException();
    }
    @Test void staleVersionCannotOverwriteNewerPrescription() {
        var current=new VisitPrescriptionResponse(UUID.randomUUID(),4,2,"DRAFT",List.of(),"Correction",null);
        assertThatThrownBy(()->CareVisitPrescriptionService.checkVersion(current,3)).isInstanceOf(ConflictException.class);
        assertThatCode(()->CareVisitPrescriptionService.checkVersion(current,4)).doesNotThrowAnyException();
    }
    @Test void emptyDraftAllowedButUnboundedLinesRejected() {
        assertThatCode(()->CareVisitPrescriptionService.validateItems(List.of(),false)).doesNotThrowAnyException();
        var item=new Item("Drug","5mg","oral","daily","one day",null);
        assertThatThrownBy(()->CareVisitPrescriptionService.validateItems(java.util.Collections.nCopies(101,item),false)).isInstanceOf(BusinessRuleException.class);
    }
}
