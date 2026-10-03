package com.peterpork.liuyao.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPolicyTest {
    @Test fun `default provider remains SiliconFlow for existing installs`() {
        assertEquals(ProviderId.SILICON_FLOW, AppSettings().aiProvider)
    }

    @Test fun `new or outdated consent requires confirmation`() {
        assertTrue(AppSettings(experimentalConsentVersion = 0).requiresExperimentalConsent())
    }

    @Test fun `current consent can re-enable without another confirmation`() {
        assertFalse(
            AppSettings(
                experimentalProfessionalEnabled = false,
                experimentalConsentVersion = AppSettings.CURRENT_EXPERIMENTAL_CONSENT_VERSION
            ).requiresExperimentalConsent()
        )
    }
}
