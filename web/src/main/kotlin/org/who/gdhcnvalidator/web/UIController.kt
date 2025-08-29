package org.who.gdhcnvalidator.web

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.servlet.view.RedirectView
import org.who.gdhcnvalidator.QRDecoder

@Controller
class UIController(
    private val rest: GdhcnRestController
) {
    @Autowired
    lateinit var json: ObjectMapper

    @RequestMapping("/index")
    fun index() = "index"

    @GetMapping("/showCredential")
    fun showCredential() = "showCredential"

    @PostMapping("/ui/findAndVerify")
    fun uiFindAndVerify(@RequestParam("file") file: MultipartFile, model: Model): String {
        val result = rest.findAndVerify(file)                 // ✅ call the injected bean
        model.addAttribute("result", result)
        return "result"
    }

    @PostMapping("/upload")
    fun uploadQRImage(file: MultipartFile, redirect: RedirectAttributes): RedirectView {
        val result = rest.findAndVerify(file)                 // ✅ no new instance, use bean

        if (result.status == QRDecoder.Status.NOT_FOUND) {
            redirect.addFlashAttribute("error", "QR Not Found")
            return RedirectView("showCredential")
        }

        val fhir = FhirContext.forCached(FhirVersionEnum.R4)
            .newJsonParser()
            .setPrettyPrint(true)

        // pretty writer instead of mutating/autowire confusion
        val pretty = json.copy().enable(SerializationFeature.INDENT_OUTPUT)

        redirect.addFlashAttribute("status", result.status)
        redirect.addFlashAttribute("qr", result.qr)
        if (result.contents != null)
            redirect.addFlashAttribute("contents", fhir.encodeResourceToString(result.contents))
        if (result.issuer != null)
            redirect.addFlashAttribute("issuer", pretty.writeValueAsString(result.issuer))
        if (result.unpacked != null)
            redirect.addFlashAttribute("unpacked", pretty.writeValueAsString(json.readTree(result.unpacked)))

        return RedirectView("showCredential")
    }
}
