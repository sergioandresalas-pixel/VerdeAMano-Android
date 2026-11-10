package com.verdeamano

import com.verdeamano.contracts.*
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    private val valid = ProductInput("category","sku","Maceta","Maceta para balcón con drenaje","Barro","15 x 20 cm",29900,12)
    @Test fun `invalid inventory price and insecure image are rejected`() {
        assertNull(Validation.product(valid))
        assertNotNull(Validation.product(valid.copy(stock=-1)))
        assertNotNull(Validation.product(valid.copy(priceCop=0)))
        assertNotNull(Validation.product(valid.copy(imageUrl="http://example.test/photo.jpg")))
    }
    @Test fun `email validation rejects malformed input`() {
        assertTrue(Validation.email("buyer@verdeamano.test"))
        assertFalse(Validation.email("bad"))
        assertFalse(Validation.email("a @b.test"))
    }
}
