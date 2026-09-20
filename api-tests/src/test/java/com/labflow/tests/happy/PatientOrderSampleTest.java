// @layer: integration
// @spec: 003-test-automation-foundation
// @regression
package com.labflow.tests.happy;

import com.labflow.common.model.Patient;
import com.labflow.tests.support.LabTest;
import com.labflow.tests.support.UniqueData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientOrderSampleTest extends LabTest {

    @Test
    @DisplayName("A registered patient can be looked up with its lab-assigned id and name")
    void registeredPatientCanBeLookedUp() {
        String name = UniqueData.name("patient");

        Patient registered = lab.patients().register(name);
        Patient found = lab.patients().find(registered.id());

        assertThat(found.id()).isNotBlank().isEqualTo(registered.id());
        assertThat(found.name()).isEqualTo(name);
    }
}
