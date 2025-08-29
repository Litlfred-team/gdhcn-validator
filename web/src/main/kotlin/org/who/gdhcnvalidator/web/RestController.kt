package org.who.gdhcnvalidator.web

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.who.gdhcnvalidator.QRDecoder
import org.who.gdhcnvalidator.trust.CompoundRegistry
import org.who.gdhcnvalidator.trust.TrustRegistryFactory
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@RestController // <-- now unambiguous
@Tag(name = "GDHCN Validator API", description = "Digital health certificate validation services")
class GdhcnRestController { // <-- renamed from 'RestController'
    companion object {
        var registry = CompoundRegistry(TrustRegistryFactory.getTrustRegistries()).apply { init() }
        val validationService = ValidationService(registry)
    }

    data class QRContents(val uri: String)

    data class ServiceInfo(
        val serviceName: String,
        val version: String,
        val description: String,
        val swaggerUiUrl: String,
        val apiDocsUrl: String
    )

    @GetMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Service Information",
        description = "Returns basic information about the GDHCN Validator service including links to API documentation"
    )
    @ApiResponse(responseCode = "200", description = "Service information retrieved successfully")
    fun serviceInfo(): ServiceInfo = ServiceInfo(
        serviceName = "GDHCN Validator",
        version = "0.1.0",
        description = "WHO Global Digital Health Certification Network (GDHCN) Validator - REST API for validating digital health certificates",
        swaggerUiUrl = "/swagger-ui.html",
        apiDocsUrl = "/v3/api-docs"
    )

    @PostMapping("/verify")
    @Operation(
        summary = "Full QR Code Verification",
        description = "Performs complete verification of a digital health certificate QR code including format detection, signature validation, and content extraction"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Verification completed",
                content = [Content(schema = Schema(implementation = QRDecoder.VerificationResult::class))]
            ),
            ApiResponse(responseCode = "400", description = "Invalid QR code format")
        ]
    )
    fun verify(@RequestBody qr: QRContents): QRDecoder.VerificationResult =
        QRDecoder(registry).decode(qr.uri)

    @PostMapping("/findAndVerify")
    @Operation(
        summary = "Find and Verify QR Code from Image",
        description = "Extracts QR code from uploaded image file and performs full verification"
    )
    fun findAndVerify(@RequestParam("file") file: MultipartFile): QRDecoder.VerificationResult {
        if (file.isEmpty) {
            return QRDecoder.VerificationResult(QRDecoder.Status.NOT_FOUND, null, null, "", null)
        }

        val image = if (StringUtils.endsWithIgnoreCase(file.originalFilename, "pdf")) {
            val doc = Loader.loadPDF(ByteArrayInputStream(file.bytes).readAllBytes())
            val pdfRenderer = PDFRenderer(doc)
            pdfRenderer.renderImageWithDPI(0, 300f)
        } else {
            ImageIO.read(ByteArrayInputStream(file.bytes))
        }

        val binaryBitmap = BinaryBitmap(
            HybridBinarizer(BufferedImageLuminanceSource(addBordertoImage(image)))
        )

        val qrContents = try {
            MultiFormatReader().decode(binaryBitmap)
        } catch (e: NotFoundException) {
            return QRDecoder.VerificationResult(QRDecoder.Status.NOT_FOUND, null, null, "", null)
        }

        return this.verify(QRContents(qrContents.text))
    }


    // Granular validation service endpoints (kept for backwards compatibility)

    /**
     * Extract KID (Key ID) from QR code if present
     */
    @PostMapping("/validation/kid-extraction")
    @Operation(
        summary = "Extract KID from QR Code",
        description = "Extracts the Key ID (KID) from a QR code if present, without performing full verification"
    )
    fun kidExtraction(@RequestBody qr: QRContents): ValidationService.KidExtractionResult {
        return validationService.extractKid(qr.uri)
    }

    /**
     * Check which environment (DEV/UAT/PROD) a KID belongs to
     */
    @PostMapping("/validation/kid-environment")
    @Operation(
        summary = "Check KID Environment",
        description = "Determines which environment (DEV/UAT/PROD) a Key ID belongs to"
    )
    fun kidEnvironment(@RequestBody request: KidEnvironmentRequest): ValidationService.KidEnvironmentResult {
        return validationService.checkKidEnvironment(request.kid)
    }

    /**
     * Validate digital signature of QR code
     */
    @PostMapping("/validation/signature-validation")
    @Operation(
        summary = "Validate Digital Signature",
        description = "Validates the digital signature of a QR code certificate"
    )
    fun signatureValidation(@RequestBody qr: QRContents): ValidationService.SignatureValidationResult {
        return validationService.validateSignature(qr.uri)
    }

    /**
     * Extract content from QR code
     */
    @PostMapping("/validation/content-extraction")
    @Operation(
        summary = "Extract QR Code Content",
        description = "Extracts and decodes the content from a QR code certificate"
    )
    fun contentExtraction(@RequestBody qr: QRContents): ValidationService.ContentExtractionResult {
        return validationService.extractContent(qr.uri)
    }

    // Data class for KID environment request
    data class KidEnvironmentRequest(
        val kid: String
    )

    // New unified validation endpoint

    data class ValidationRequest(
        val content: String? = null
    )

    data class ValidationResponse(
        val valid: Boolean,
        val format: String?,
        val issuer: String?,
        val details: ValidationDetails?
    )

    data class ValidationDetails(
        val status: String,
        val kid: String?,
        val environment: String?,
        val signatureValid: Boolean,
        val content: String?
    )

    /**
     * Unified QR Code Validation
     */
    @PostMapping("/validation/validate-code")
    @Tag(name = "Validation API", description = "Unified QR code validation")
    @Operation(
        summary = "Validate QR Code",
        description = "Validates a QR code either from string content or uploaded image. Supports optional format filtering."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Valid certificate", content = [Content(schema = Schema(implementation = ValidationResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid input format"),
        ApiResponse(responseCode = "404", description = "QR code format not recognized"),
        ApiResponse(responseCode = "422", description = "Invalid certificate content")
    ])
    fun validateCode(
        @RequestParam(value = "format", required = false) format: String?,
        @RequestParam(value = "file", required = false) file: MultipartFile?,
        @RequestBody(required = false) request: ValidationRequest?
    ): ResponseEntity<ValidationResponse> {

        val qrFormat: String? = format?.trim()?.ifEmpty { null }

        // Determine QR content source
        val qrContent = when {
            file != null && !file.isEmpty -> extractQRFromImage(file)
            request?.content != null -> request.content
            else -> return ResponseEntity.badRequest().body(
                ValidationResponse(
                    false, null, null,
                    ValidationDetails("INVALID_INPUT", null, null, false, "No QR content or image provided")
                )
            )
        }

        val content = qrContent ?: return ResponseEntity.status(404).body(
            ValidationResponse(
                valid = false,
                format = "UNKNOWN",
                issuer = null,
                details = ValidationDetails(
                    status = "QR_NOT_FOUND",
                    kid = null,
                    environment = null,
                    signatureValid = false,
                    content = "QR code not found in image"
                )
            )
        )



        // Check format filtering - for now, focus on HC1
        if (qrFormat != null) {
            val supportedFormat = when (qrFormat.uppercase()) {
                "HC1", "HCERT" -> "HC1"
                else -> return ResponseEntity.badRequest().body(
                    ValidationResponse(
                        false, null, null,
                        ValidationDetails(
                            "UNSUPPORTED_FORMAT", null, null, false,
                            "Format '$qrFormat' not supported. Currently only HC1 is supported."
                        )
                    )
                )
            }

            if (!content.uppercase().startsWith("$supportedFormat:")) {
                return ResponseEntity.status(422).body(
                    ValidationResponse(
                        valid = false,
                        format = "HC1",
                        issuer = null,
                        details = ValidationDetails(
                            status = "INVALID_ENCODING",
                            kid = null,
                            environment = null,
                            signatureValid = false,
                            content = "Invalid QR code encoding"
                        )
                    )
                )
            }
        }


        if (qrFormat == null && !content.uppercase().startsWith("HC1:")) {
            return ResponseEntity.status(404).body(
    ValidationResponse(
        valid = false,
        format = "UNKNOWN",
        issuer = null,
        details = ValidationDetails(
            status = "NOT_SUPPORTED",
            kid = null,
            environment = null,
            signatureValid = false,
            content = "QR code format not supported"
        )
    )
            )
        }


        // Perform validation
        val verificationResult = QRDecoder(registry).decode(content)
        
        return when (verificationResult.status) {
            QRDecoder.Status.VERIFIED -> ResponseEntity.ok(
    ValidationResponse(
        valid = false,
        format = "HC1",
        issuer = null,
        details = ValidationDetails(
            status = "INVALID_ENCODING",
            kid = null,
            environment = null,
            signatureValid = false,
            content = "Invalid QR code encoding"
        )
    )
            )
        QRDecoder.Status.NOT_SUPPORTED -> ResponseEntity.status(404).body(
            ValidationResponse(
                valid = false,
                format = "UNKNOWN",
                issuer = null,
                details = ValidationDetails(
                    status = "NOT_SUPPORTED",
                    kid = null,
                    environment = null,
                    signatureValid = false,
                    content = "QR code format not supported"
                )
            )
        )

        QRDecoder.Status.INVALID_ENCODING -> ResponseEntity.status(422).body(
            ValidationResponse(
                valid = false,
                format = "HC1",
                issuer = null,
                details = ValidationDetails(
                    status = "INVALID_ENCODING",
                    kid = null,
                    environment = null,
                    signatureValid = false,
                    content = "Invalid QR code encoding"
                )
            )
        )

        QRDecoder.Status.INVALID_SIGNATURE -> ResponseEntity.status(422).body(
            ValidationResponse(
                valid = false,
                format = "HC1",
                issuer = verificationResult.issuer?.displayName?.values?.firstOrNull(),
                details = ValidationDetails(
                    status = "INVALID_SIGNATURE",
                    kid = extractKidFromResult(verificationResult),
                    environment = verificationResult.issuer?.scope?.name,
                    signatureValid = false,
                    content = verificationResult.unpacked
                )
            )
        )

        QRDecoder.Status.ISSUER_NOT_TRUSTED -> ResponseEntity.status(422).body(
            ValidationResponse(
                valid = false,
                format = "HC1",
                issuer = null,
                details = ValidationDetails(
                    status = "ISSUER_NOT_TRUSTED",
                    kid = extractKidFromResult(verificationResult),
                    environment = null,
                    signatureValid = false,
                    content = verificationResult.unpacked
                )
            )
        )

            else -> ResponseEntity.status(422).body(
    ValidationResponse(
        valid = false,
        format = "HC1",
        issuer = null,
        details = ValidationDetails(
            status = "ISSUER_NOT_TRUSTED",
            kid = extractKidFromResult(verificationResult),
            environment = null,
            signatureValid = false,
            content = verificationResult.unpacked
        )
    )
            )
        }
    }

    private fun extractQRFromImage(file: MultipartFile): String? {
        return try {
            val image = if (StringUtils.endsWithIgnoreCase(file.originalFilename, "pdf")) {
                val doc = Loader.loadPDF(ByteArrayInputStream(file.bytes).readAllBytes())
                val pdfRenderer = PDFRenderer(doc)
                pdfRenderer.renderImageWithDPI(0, 300f)
            } else {
                ImageIO.read(ByteArrayInputStream(file.bytes))
            }

            val binaryBitmap = BinaryBitmap(HybridBinarizer(
                BufferedImageLuminanceSource(addBordertoImage(image))
            ))

            val qrContents = MultiFormatReader().decode(binaryBitmap)
            qrContents.text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractKidFromResult(result: QRDecoder.VerificationResult): String? {
        // This is a simplified approach - in a real implementation, 
        // we would extract the actual KID from the certificate structure
        return "extracted_kid_placeholder"
    }

    fun addBordertoImage(source: BufferedImage, color: Color = Color.WHITE, borderLeft: Int = 50, borderTop: Int = 50): BufferedImage {
        val borderedImageWidth: Int = source.width + borderLeft * 2
        val borderedImageHeight: Int = source.height + borderTop * 2
        val img = BufferedImage(borderedImageWidth, borderedImageHeight, source.type)
        img.createGraphics()
        val g = img.graphics as Graphics2D
        g.color = color
        g.fillRect(0, 0, borderedImageWidth, borderedImageHeight)
        g.drawImage(
            source,
            borderLeft,
            borderTop,
            source.width + borderLeft,
            source.height + borderTop,
            0,
            0,
            source.width,
            source.height,
            Color.BLACK,
            null
        )
        return img
    }
}