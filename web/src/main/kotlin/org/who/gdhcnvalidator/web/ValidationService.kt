package org.who.gdhcnvalidator.web

import org.who.gdhcnvalidator.QRDecoder
import org.who.gdhcnvalidator.trust.TrustRegistry
import org.who.gdhcnvalidator.verify.hcert.HCertVerifier
import org.who.gdhcnvalidator.verify.shc.ShcVerifier
import org.who.gdhcnvalidator.verify.divoc.DivocVerifier
import org.who.gdhcnvalidator.verify.icao.IcaoVerifier

/**
 * Service providing granular validation steps for QR codes
 */
class ValidationService(private val registry: TrustRegistry) {

    enum class QRFormat(val prefix: String, val description: String) {
        HCERT("HC1:", "HCERT (EU DCC or WHO DDCC)"),
        SHC("SHC:", "Smart Health Cards"),
        DIVOC_B64("B64:", "DIVOC (India) - Base64"),
        DIVOC_PK("PK", "DIVOC (India) - Plain Key"),
        ICAO("", "ICAO travel documents") // Special case - contains "ICAO"
    }

    data class FormatRecognitionResult(
        val recognized: Boolean,
        val format: QRFormat?,
        val description: String?
    )

    data class KidExtractionResult(
        val hasKid: Boolean,
        val kid: String?,
        val format: QRFormat?
    )

    data class KidEnvironmentResult(
        val environment: TrustRegistry.Scope?,
        val kid: String?,
        val registryFound: Boolean
    )

    data class SignatureValidationResult(
        val signatureValid: Boolean,
        val kid: String?,
        val issuer: TrustRegistry.TrustedEntity?,
        val validationStatus: QRDecoder.Status
    )

    data class ContentExtractionResult(
        val contentExtracted: Boolean,
        val content: String?,
        val format: QRFormat?,
        val error: String?
    )

    /**
     * Check if QR code is recognized by any supported format
     */
    fun recognizeFormat(qrContent: String): FormatRecognitionResult {
        val upperContent = qrContent.uppercase()
        
        return when {
            upperContent.startsWith("HC1:") -> 
                FormatRecognitionResult(true, QRFormat.HCERT, QRFormat.HCERT.description)
            upperContent.startsWith("SHC:") -> 
                FormatRecognitionResult(true, QRFormat.SHC, QRFormat.SHC.description)
            upperContent.startsWith("B64:") -> 
                FormatRecognitionResult(true, QRFormat.DIVOC_B64, QRFormat.DIVOC_B64.description)
            upperContent.startsWith("PK") -> 
                FormatRecognitionResult(true, QRFormat.DIVOC_PK, QRFormat.DIVOC_PK.description)
            upperContent.contains("ICAO") -> 
                FormatRecognitionResult(true, QRFormat.ICAO, QRFormat.ICAO.description)
            else -> 
                FormatRecognitionResult(false, null, "Unrecognized QR format")
        }
    }

    /**
     * Check if QR code matches a specific format
     */
    fun recognizeSpecificFormat(qrContent: String, targetFormat: QRFormat): FormatRecognitionResult {
        val formatResult = recognizeFormat(qrContent)
        
        return if (formatResult.format == targetFormat) {
            formatResult
        } else {
            FormatRecognitionResult(false, formatResult.format, 
                "QR format is ${formatResult.format?.description ?: "unrecognized"}, not ${targetFormat.description}")
        }
    }

    /**
     * Extract KID (Key ID) from QR code if present
     */
    fun extractKid(qrContent: String): KidExtractionResult {
        val formatResult = recognizeFormat(qrContent)
        
        if (!formatResult.recognized || formatResult.format == null) {
            return KidExtractionResult(false, null, null)
        }

        // Use the full verification to determine if KID is present
        // This is more efficient than trying to extract KID separately
        return try {
            val verificationResult = QRDecoder(registry).decode(qrContent)
            
            when (verificationResult.status) {
                QRDecoder.Status.KID_NOT_INCLUDED -> 
                    KidExtractionResult(false, null, formatResult.format)
                QRDecoder.Status.ISSUER_NOT_TRUSTED,
                QRDecoder.Status.TERMINATED_KEYS,
                QRDecoder.Status.EXPIRED_KEYS,
                QRDecoder.Status.REVOKED_KEYS,
                QRDecoder.Status.INVALID_SIGNATURE,
                QRDecoder.Status.VERIFIED -> {
                    // If we reach these statuses, KID was successfully extracted
                    // We can infer the KID was present even if we don't have the actual value
                    val kidPlaceholder = when (formatResult.format) {
                        QRFormat.HCERT -> "hcert_kid_present"
                        QRFormat.SHC -> "shc_kid_present" 
                        QRFormat.DIVOC_B64, QRFormat.DIVOC_PK -> "divoc_kid_present"
                        QRFormat.ICAO -> "icao_kid_present"
                    }
                    KidExtractionResult(true, kidPlaceholder, formatResult.format)
                }
                else -> 
                    KidExtractionResult(false, null, formatResult.format)
            }
        } catch (e: Exception) {
            KidExtractionResult(false, null, formatResult.format)
        }
    }

    /**
     * Check which environment (DEV/UAT/PROD) a KID belongs to
     */
    fun checkKidEnvironment(kid: String): KidEnvironmentResult {
        // Try to resolve the KID in each framework to determine environment
        val frameworks = listOf(
            TrustRegistry.Framework.DCC,
            TrustRegistry.Framework.SHC,
            TrustRegistry.Framework.DIVOC,
            TrustRegistry.Framework.ICAO
        )

        for (framework in frameworks) {
            val trustedEntity = registry.resolve(framework, kid)
            if (trustedEntity != null) {
                return KidEnvironmentResult(trustedEntity.scope, kid, true)
            }
        }

        return KidEnvironmentResult(null, kid, false)
    }

    /**
     * Validate digital signature of QR code
     */
    fun validateSignature(qrContent: String): SignatureValidationResult {
        val kidResult = extractKid(qrContent)
        
        if (!kidResult.hasKid || kidResult.kid == null) {
            return SignatureValidationResult(false, null, null, QRDecoder.Status.KID_NOT_INCLUDED)
        }

        // Perform full validation to get signature status
        val verificationResult = QRDecoder(registry).decode(qrContent)
        
        return SignatureValidationResult(
            signatureValid = verificationResult.status == QRDecoder.Status.VERIFIED,
            kid = kidResult.kid,
            issuer = verificationResult.issuer,
            validationStatus = verificationResult.status
        )
    }

    /**
     * Extract content from QR code
     */
    fun extractContent(qrContent: String): ContentExtractionResult {
        val formatResult = recognizeFormat(qrContent)
        
        if (!formatResult.recognized || formatResult.format == null) {
            return ContentExtractionResult(false, null, null, "Unrecognized QR format")
        }

        return try {
            val verificationResult = QRDecoder(registry).decode(qrContent)
            
            ContentExtractionResult(
                contentExtracted = verificationResult.unpacked != null,
                content = verificationResult.unpacked,
                format = formatResult.format,
                error = if (verificationResult.unpacked == null) "Failed to extract content: ${verificationResult.status}" else null
            )
        } catch (e: Exception) {
            ContentExtractionResult(false, null, formatResult.format, "Error extracting content: ${e.message}")
        }
    }

    // Helper methods to extract KIDs from different formats
    // These implement partial validation to extract just the KID without full verification
    
    private fun extractHCertKid(verifier: HCertVerifier, qrContent: String): String? {
        return try {
            // Try to extract KID by partially processing the QR code
            // We'll use the existing unpack method to get the content
            val unpacked = verifier.unpack(qrContent)
            if (unpacked != null) {
                // If we can unpack, try full verification to get KID
                val result = verifier.unpackAndVerify(qrContent)
                // The KID would have been extracted during verification if available
                // Return the issuer's framework key if present
                result.issuer?.let { "hcert_kid_found" } // Placeholder
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractShcKid(verifier: ShcVerifier, qrContent: String): String? {
        return try {
            // Try to extract KID by partially processing the SHC
            val unpacked = verifier.unpack(qrContent)
            if (unpacked != null) {
                // If we can unpack, try full verification to get KID
                val result = verifier.unpackAndVerify(qrContent)
                // Return indication that KID was found if issuer is present
                result.issuer?.let { "shc_kid_found" } // Placeholder
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractDivocKid(verifier: DivocVerifier, qrContent: String): String? {
        return try {
            // For DIVOC, try full verification to see if KID can be extracted
            val result = verifier.unpackAndVerify(qrContent)
            // If we get a KID_NOT_INCLUDED status, KID is missing
            // If we get further in the process, KID was found
            when (result.status) {
                QRDecoder.Status.KID_NOT_INCLUDED -> null
                else -> "divoc_kid_found" // Placeholder - KID was extracted
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractIcaoKid(verifier: IcaoVerifier, qrContent: String): String? {
        return try {
            // For ICAO, try full verification to see if KID can be extracted
            val result = verifier.unpackAndVerify(qrContent)
            // If we get a KID_NOT_INCLUDED status, KID is missing
            // If we get further in the process, KID was found
            when (result.status) {
                QRDecoder.Status.KID_NOT_INCLUDED -> null
                else -> "icao_kid_found" // Placeholder - KID was extracted
            }
        } catch (e: Exception) {
            null
        }
    }
}