package org.who.gdhcnvalidator.web.demo

import org.who.gdhcnvalidator.trust.CompoundRegistry
import org.who.gdhcnvalidator.trust.TrustRegistryFactory
import org.who.gdhcnvalidator.web.ValidationService

/**
 * Simple demonstration of ValidationService functionality
 */
object ValidationServiceDemo {
    
    @JvmStatic
    fun main(args: Array<String>) {
        // Initialize trust registry
        val registry = CompoundRegistry(TrustRegistryFactory.getTrustRegistries()).apply {
            init()
        }
        
        val validationService = ValidationService(registry)
        
        // Test sample QR codes
        val sampleQRs = mapOf(
            "HCERT" to "HC1:6BF+70790T9WJWG.FKY*4GO0RKPNHILCJ",
            "SHC" to "shc:/567629095243206034602924374044603122295953265460346029254077280433602870",
            "DIVOC" to "B64:UEsDBBQACAAIAAAAAAAAAAAAAAAAAAAAAAAQAAAAY2VydGlmaWNhdGUuanNvb",
            "ICAO" to "ICAO:TKN01234567890ABC",
            "Unknown" to "UNKNOWN:123456789"
        )
        
        println("=== ValidationService Demonstration ===")
        println()
        
        for ((name, qr) in sampleQRs) {
            println("Testing $name QR: ${qr.take(30)}...")
            
            // Test format recognition
            val formatResult = validationService.recognizeFormat(qr)
            println("  Format Recognition: ${formatResult.recognized} - ${formatResult.description}")
            
            if (formatResult.recognized && formatResult.format != null) {
                // Test KID extraction
                val kidResult = validationService.extractKid(qr)
                println("  KID Extraction: ${kidResult.hasKid} - ${kidResult.kid}")
                
                // Test content extraction
                val contentResult = validationService.extractContent(qr)
                println("  Content Extraction: ${contentResult.contentExtracted}")
                if (contentResult.error != null) {
                    println("    Error: ${contentResult.error}")
                }
                
                // Test signature validation
                val signatureResult = validationService.validateSignature(qr)
                println("  Signature Validation: ${signatureResult.signatureValid} - ${signatureResult.validationStatus}")
            }
            
            println()
        }
        
        // Test KID environment check
        println("Testing KID Environment Check:")
        val kidEnvResult = validationService.checkKidEnvironment("test_kid_123")
        println("  KID: ${kidEnvResult.kid}")
        println("  Environment: ${kidEnvResult.environment}")
        println("  Registry Found: ${kidEnvResult.registryFound}")
        
        println()
        println("=== Demo Complete ===")
    }
}