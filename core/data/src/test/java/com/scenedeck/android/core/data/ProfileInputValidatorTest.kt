package com.scenedeck.android.core.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileInputValidatorTest {

    @Test
    fun nameValidation() {
        assertNotNull(ProfileInputValidator.validateName(""))
        assertNotNull(ProfileInputValidator.validateName("   "))
        assertNull(ProfileInputValidator.validateName("Home PC"))
    }

    @Test
    fun hostValidation() {
        assertNotNull(ProfileInputValidator.validateHost(""))
        assertNotNull(ProfileInputValidator.validateHost("has space"))
        assertNotNull(ProfileInputValidator.validateHost("http://192.168.1.1"))
        assertNotNull(ProfileInputValidator.validateHost("bad_host!"))
        assertNull(ProfileInputValidator.validateHost("192.168.1.20"))
        assertNull(ProfileInputValidator.validateHost("studio.local"))
        assertNull(ProfileInputValidator.validateHost("my-pc"))
    }

    @Test
    fun portValidation() {
        assertNotNull(ProfileInputValidator.validatePort(""))
        assertNotNull(ProfileInputValidator.validatePort("abc"))
        assertNotNull(ProfileInputValidator.validatePort("0"))
        assertNotNull(ProfileInputValidator.validatePort("65536"))
        assertNull(ProfileInputValidator.validatePort("4455"))
        assertNull(ProfileInputValidator.validatePort("1"))
        assertNull(ProfileInputValidator.validatePort("65535"))
    }

    @Test
    fun isValidCombination() {
        assert(ProfileInputValidator.isValid("Home", "192.168.1.20", "4455"))
        assert(!ProfileInputValidator.isValid("", "192.168.1.20", "4455"))
        assert(!ProfileInputValidator.isValid("Home", "", "4455"))
        assert(!ProfileInputValidator.isValid("Home", "192.168.1.20", "0"))
    }
}
