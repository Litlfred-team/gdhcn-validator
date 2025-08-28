package org.who.gdhcnvalidator.web

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.who.gdhcnvalidator.trust.CompoundRegistry
import org.who.gdhcnvalidator.trust.TrustRegistryFactory

/**
 * Integration tests for ValidationService endpoints via RestController
 */
class ValidationServiceIntegrationTest {
    
    private lateinit var restController: RestController
    
    // Sample QR codes from test resources (simplified for testing)
    private val hcertQR = "HC1:6BF+70790T9WJWG.FKY*4GO0"
    private val shcQR = "shc:/567629095243206034602924"
    private val divocQR = "B64:UEsDBBQACAAIAAAAAAAAAAAAAAAAAAAAAAAQAAAAY2VydGlmaWNhdGUuanNvb"
    private val unknownQR = "UNKNOWN:123456789"
    
    @BeforeEach
    fun setUp() {
        // Initialize with the same registry used by RestController
        val registry = CompoundRegistry(TrustRegistryFactory.getTrustRegistries()).apply {
            init()
        }
        restController = RestController()
    }
    
    @Test
    fun testFormatRecognitionEndpoint() {
        val request = RestController.QRContents(hcertQR)
        val result = restController.formatRecognition(request)
        
        assertTrue(result.recognized)
        assertEquals(ValidationService.QRFormat.HCERT, result.format)
        assertEquals("HCERT (EU DCC or WHO DDCC)", result.description)
    }
    
    @Test
    fun testSpecificFormatRecognitionEndpoint_Match() {
        val request = RestController.QRContents(hcertQR)
        val result = restController.specificFormatRecognition(request, "hcert")
        
        assertTrue(result.recognized)
        assertEquals(ValidationService.QRFormat.HCERT, result.format)
    }
    
    @Test
    fun testSpecificFormatRecognitionEndpoint_NoMatch() {
        val request = RestController.QRContents(hcertQR)
        val result = restController.specificFormatRecognition(request, "shc")
        
        assertFalse(result.recognized)
        assertTrue(result.description!!.contains("not Smart Health Cards"))
    }
    
    @Test
    fun testSpecificFormatRecognitionEndpoint_InvalidFormat() {
        val request = RestController.QRContents(hcertQR)
        val result = restController.specificFormatRecognition(request, "invalid")
        
        assertFalse(result.recognized)
        assertTrue(result.description!!.contains("Unknown format"))
    }
    
    @Test
    fun testKidExtractionEndpoint() {
        val request = RestController.QRContents(hcertQR)
        val result = restController.kidExtraction(request)
        
        assertNotNull(result)
        assertEquals(ValidationService.QRFormat.HCERT, result.format)
        // The result depends on the actual QR content and trust registry
    }
    
    @Test
    fun testKidEnvironmentEndpoint() {
        val request = RestController.KidEnvironmentRequest("test_kid_123")
        val result = restController.kidEnvironment(request)
        
        assertNotNull(result)
        assertEquals("test_kid_123", result.kid)
        // Registry found result depends on the trust registry configuration
    }
    
    @Test
    fun testContentExtractionEndpoint() {
        val request = RestController.QRContents(hcertQR)
        val result = restController.contentExtraction(request)
        
        assertNotNull(result)
        assertEquals(ValidationService.QRFormat.HCERT, result.format)
        // Content extraction success depends on QR validity
    }
    
    @Test
    fun testSignatureValidationEndpoint() {
        val request = RestController.QRContents(hcertQR)
        val result = restController.signatureValidation(request)
        
        assertNotNull(result)
        // Signature validation result depends on trust registry and QR validity
    }
    
    @Test
    fun testFormatRecognition_AllFormats() {
        // Test HCERT
        val hcertResult = restController.formatRecognition(RestController.QRContents(hcertQR))
        assertEquals(ValidationService.QRFormat.HCERT, hcertResult.format)
        
        // Test SHC
        val shcResult = restController.formatRecognition(RestController.QRContents(shcQR))
        assertEquals(ValidationService.QRFormat.SHC, shcResult.format)
        
        // Test DIVOC
        val divocResult = restController.formatRecognition(RestController.QRContents(divocQR))
        assertEquals(ValidationService.QRFormat.DIVOC_B64, divocResult.format)
        
        // Test Unknown
        val unknownResult = restController.formatRecognition(RestController.QRContents(unknownQR))
        assertFalse(unknownResult.recognized)
        assertNull(unknownResult.format)
    }
    
    @Test
    fun testAllSupportedFormats() {
        val formats = listOf("hcert", "shc", "divoc_b64", "divoc_pk", "icao")
        
        for (format in formats) {
            val result = restController.specificFormatRecognition(
                RestController.QRContents(unknownQR), 
                format
            )
            assertFalse(result.recognized) // Unknown QR should not match any format
        }
    }
}