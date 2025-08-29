package org.who.gdhcnvalidator.web

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.http.MediaType
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
import kotlin.String
import kotlin.apply


@RestController
@Tag(name = "GDHCN Validator API", description = "Digital health certificate validation services")
class RestController {
    companion object {
        var registry = CompoundRegistry(TrustRegistryFactory.getTrustRegistries()).apply {
            init()
        }
        
        // Initialize validation service
        val validationService = ValidationService(registry)
    }

    data class QRContents(
        val uri: String,
    )

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
    fun serviceInfo(): ServiceInfo {
        return ServiceInfo(
            serviceName = "GDHCN Validator",
            version = "0.1.0",
            description = "WHO Global Digital Health Certification Network (GDHCN) Validator - REST API for validating digital health certificates",
            swaggerUiUrl = "/swagger-ui.html",
            apiDocsUrl = "/v3/api-docs"
        )
    }

    @PostMapping("/verify")
    @Operation(
        summary = "Full QR Code Verification",
        description = "Performs complete verification of a digital health certificate QR code including format detection, signature validation, and content extraction"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Verification completed", content = [Content(schema = Schema(implementation = QRDecoder.VerificationResult::class))]),
        ApiResponse(responseCode = "400", description = "Invalid QR code format")
    ])
    fun verify(@RequestBody qr: QRContents): QRDecoder.VerificationResult {
        return QRDecoder(registry).decode(qr.uri)
    }

    @PostMapping("/findAndVerify")
    @Operation(
        summary = "Find and Verify QR Code from Image",
        description = "Extracts QR code from uploaded image file and performs full verification"
    )
    fun findAndVerify(@RequestParam("file") file: MultipartFile): QRDecoder.VerificationResult {
        if (file.isEmpty()) {
            return QRDecoder.VerificationResult(QRDecoder.Status.NOT_FOUND, null, null, "", null)
        }

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

        val qrContents = try {
            MultiFormatReader().decode(binaryBitmap)
        } catch (e: NotFoundException) {
            return QRDecoder.VerificationResult(QRDecoder.Status.NOT_FOUND, null, null, "", null)
        }

        return this.verify(QRContents(qrContents.text))
    }

    // New validation service endpoints

    /**
     * Check if QR code is recognized by any supported format
     */
    @PostMapping("/validation/format-recognition")
    @Tag(name = "Validation Services", description = "Step-by-step validation services")
    @Operation(
        summary = "Format Recognition",
        description = "Identifies the format of a QR code (HCERT, SHC, DIVOC, ICAO) without performing full validation"
    )
    fun formatRecognition(@RequestBody qr: QRContents): ValidationService.FormatRecognitionResult {
        return validationService.recognizeFormat(qr.uri)
    }

    /**
     * Check if QR code matches a specific format
     */
    @PostMapping("/validation/format-recognition/{format}")
    @Tag(name = "Validation Services")
    @Operation(
        summary = "Specific Format Recognition",
        description = "Checks if a QR code matches a specific format"
    )
    fun specificFormatRecognition(
        @RequestBody qr: QRContents,
        @Parameter(description = "Format to check (HCERT, SHC, DIVOC_B64, DIVOC_PK, ICAO)")
        @PathVariable format: String
    ): ValidationService.FormatRecognitionResult {
        val targetFormat = try {
            when (format.uppercase()) {
                "HCERT", "HC1" -> ValidationService.QRFormat.HCERT
                "SHC" -> ValidationService.QRFormat.SHC
                "DIVOC_B64", "B64" -> ValidationService.QRFormat.DIVOC_B64
                "DIVOC_PK", "PK" -> ValidationService.QRFormat.DIVOC_PK
                "ICAO" -> ValidationService.QRFormat.ICAO
                else -> return ValidationService.FormatRecognitionResult(
                    false, null, "Unknown format: $format"
                )
            }
        } catch (e: Exception) {
            return ValidationService.FormatRecognitionResult(
                false, null, "Invalid format parameter: $format"
            )
        }

        return validationService.recognizeSpecificFormat(qr.uri, targetFormat)
    }

    /**
     * Extract KID (Key ID) from QR code if present
     */
    @PostMapping("/validation/kid-extraction")
    @Tag(name = "Validation Services")
    @Operation(
        summary = "Key ID Extraction",
        description = "Extracts the Key ID (KID) from a QR code if present in the certificate headers"
    )
    fun kidExtraction(@RequestBody qr: QRContents): ValidationService.KidExtractionResult {
        return validationService.extractKid(qr.uri)
    }

    /**
     * Check which environment (DEV/UAT/PROD) a KID belongs to
     */
    @PostMapping("/validation/kid-environment")
    @Tag(name = "Validation Services")
    @Operation(
        summary = "Key ID Environment Check",
        description = "Determines which environment (PRODUCTION, ACCEPTANCE_TEST, DEV_STAGING) a Key ID belongs to"
    )
    fun kidEnvironment(@RequestBody request: KidEnvironmentRequest): ValidationService.KidEnvironmentResult {
        return validationService.checkKidEnvironment(request.kid)
    }

    /**
     * Validate digital signature of QR code
     */
    @PostMapping("/validation/signature-validation")
    @Tag(name = "Validation Services")
    @Operation(
        summary = "Signature Validation",
        description = "Validates the digital signature of a QR code certificate with detailed status information"
    )
    fun signatureValidation(@RequestBody qr: QRContents): ValidationService.SignatureValidationResult {
        return validationService.validateSignature(qr.uri)
    }

    /**
     * Extract content from QR code
     */
    @PostMapping("/validation/content-extraction")
    @Tag(name = "Validation Services")
    @Operation(
        summary = "Content Extraction",
        description = "Extracts and decodes the content from a QR code without signature validation"
    )
    fun contentExtraction(@RequestBody qr: QRContents): ValidationService.ContentExtractionResult {
        return validationService.extractContent(qr.uri)
    }

    // Data class for KID environment request
    data class KidEnvironmentRequest(
        val kid: String
    )

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