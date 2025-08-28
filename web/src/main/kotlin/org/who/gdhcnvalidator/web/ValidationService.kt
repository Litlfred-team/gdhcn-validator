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
     * This method performs minimal decoding to extract KID without full verification
     */
    fun extractKid(qrContent: String): KidExtractionResult {
        val formatResult = recognizeFormat(qrContent)
        
        if (!formatResult.recognized || formatResult.format == null) {
            return KidExtractionResult(false, null, null)
        }

        // For all formats, we'll use a lightweight approach:
        // Try to decode just enough to extract the KID, without full verification
        return try {
            when (formatResult.format) {
                ValidationService.QRFormat.HCERT -> extractHCertKidMinimal(qrContent, formatResult.format)
                ValidationService.QRFormat.SHC -> extractShcKidMinimal(qrContent, formatResult.format)
                ValidationService.QRFormat.DIVOC_B64, ValidationService.QRFormat.DIVOC_PK -> 
                    extractDivocKidMinimal(qrContent, formatResult.format)
                ValidationService.QRFormat.ICAO -> extractIcaoKidMinimal(qrContent, formatResult.format)
            }
        } catch (e: Exception) {
            KidExtractionResult(false, null, formatResult.format)
        }
    }

    private fun extractHCertKidMinimal(qrContent: String, format: QRFormat): KidExtractionResult {
        return try {
            val hcertVerifier = HCertVerifier(registry)
            // Try to unpack just the structure to see if KID can be extracted
            val unpacked = hcertVerifier.unpack(qrContent)
            if (unpacked != null) {
                // If unpacking succeeds, likely has a KID - run minimal verification to check
                val result = hcertVerifier.unpackAndVerify(qrContent)
                when (result.status) {
                    QRDecoder.Status.KID_NOT_INCLUDED -> KidExtractionResult(false, null, format)
                    else -> {
                        // KID was found (even if other validation failed)
                        val actualKid = "extracted_from_hcert" // Placeholder for actual KID
                        KidExtractionResult(true, actualKid, format)
                    }
                }
            } else {
                KidExtractionResult(false, null, format)
            }
        } catch (e: Exception) {
            KidExtractionResult(false, null, format)
        }
    }

    private fun extractShcKidMinimal(qrContent: String, format: QRFormat): KidExtractionResult {
        return try {
            val shcVerifier = ShcVerifier(registry)
            // Try to unpack just the structure to see if KID can be extracted
            val unpacked = shcVerifier.unpack(qrContent)
            if (unpacked != null) {
                // If unpacking succeeds, try minimal verification to check for KID
                val result = shcVerifier.unpackAndVerify(qrContent)
                when (result.status) {
                    QRDecoder.Status.KID_NOT_INCLUDED -> KidExtractionResult(false, null, format)
                    else -> {
                        // KID was found (even if other validation failed)
                        val actualKid = "extracted_from_shc" // Placeholder for actual KID
                        KidExtractionResult(true, actualKid, format)
                    }
                }
            } else {
                KidExtractionResult(false, null, format)
            }
        } catch (e: Exception) {
            KidExtractionResult(false, null, format)
        }
    }

    private fun extractDivocKidMinimal(qrContent: String, format: QRFormat): KidExtractionResult {
        return try {
            val divocVerifier = DivocVerifier(registry)
            // For DIVOC, KID extraction requires more processing - run verification to check
            val result = divocVerifier.unpackAndVerify(qrContent)
            when (result.status) {
                QRDecoder.Status.KID_NOT_INCLUDED -> KidExtractionResult(false, null, format)
                else -> {
                    // KID was found (even if other validation failed)
                    val actualKid = "extracted_from_divoc" // Placeholder for actual KID
                    KidExtractionResult(true, actualKid, format)
                }
            }
        } catch (e: Exception) {
            KidExtractionResult(false, null, format)
        }
    }

    private fun extractIcaoKidMinimal(qrContent: String, format: QRFormat): KidExtractionResult {
        return try {
            val icaoVerifier = IcaoVerifier(registry)
            // For ICAO, KID extraction requires processing - run verification to check
            val result = icaoVerifier.unpackAndVerify(qrContent)
            when (result.status) {
                QRDecoder.Status.KID_NOT_INCLUDED -> KidExtractionResult(false, null, format)
                else -> {
                    // KID was found (even if other validation failed)
                    val actualKid = "extracted_from_icao" // Placeholder for actual KID
                    KidExtractionResult(true, actualKid, format)
                }
            }
        } catch (e: Exception) {
            KidExtractionResult(false, null, format)
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

}