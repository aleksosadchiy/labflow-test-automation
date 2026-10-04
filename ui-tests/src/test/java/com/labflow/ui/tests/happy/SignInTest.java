// @layer: e2e
// @spec: 004-ui-automation-page-objects
package com.labflow.ui.tests.happy;

import com.labflow.common.junit.Smoke;
import com.labflow.ui.tests.support.UiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignInTest extends UiTest {

    @Test
    @Smoke
    @DisplayName("Signing in with the lab's valid details opens the Sample List screen")
    void validSignInOpensSampleList() {
        assertThat(signedIn().isShown()).isTrue();
    }
}
