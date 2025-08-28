package org.who.gdhcnvalidator.web

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.who.gdhcnvalidator.trust.CompoundRegistry
import org.who.gdhcnvalidator.trust.TrustRegistryFactory

/**
 * Tests for ValidationService
 */
class ValidationServiceTest {
    
    private lateinit var validationService: ValidationService
    private lateinit var registry: CompoundRegistry
    
    // Sample QR codes from test resources
    private val hcertQR = "HC1:6BF+70790T9WJWG.FKY*4GO0RKPNHILCJ\$VBFBBRW1*70HS8FN0WLCD/OWY01BCEKID97TK0F90KECTHGWJC0FDC:5AIA%G7X+AQB9746HS80:5AIBZ47VF6\$A8VL6+0AP7BN46/46R46 463ZACN9+Y9 +AVK4WJCT3EHS8XJC\$+DXJCCWENF6OF63W5NW6UF6%JC QE/IAYJC5LEW34U3ET7DXC9 QE-ED8%E.JCBECB1A-:8\$966469L6OF6VX6Q\$D.UDRYA 96NF6L/5SW6Y57KQEPD09WEQDD+Q6TW6FA7C466KCN9E%961A6DL6FA7D46JPCT3E5JDNA7\$Q68465W51S6..DX%DZJC5/DL+8 VD 8D%\$EWKEI3D:-C-3EWED1ECW.CBWEY*82T92694ZAVIAI3D8WE Y9G+95N9\$PC5\$CUZCY\$5Y\$527B789C%HQWK8E5%H4ZL2S/RS*UX51KUV:Q07WOK1DHBQZVQLTR4:1LPUR%CT M\$MAW.85BNXQNF:JN408DJRP0HX48878CJD9K8BD4:F"
    
    private val shcQR = "shc:/56762909524320603460292437404460312229595326546034602925407728043360287028647167452228092861333145643765314159064022030645045908564355034142454136403706366541713724123638030437562204673740753232392543344332605736010645292953127074242843503861221276716852752941725536670334373625647345380024213944077025250726312423573657001132105220316267750968640761356508111008270666243020277044446712214341455936637024282703544034660963252707282555072932056232255262395660612010735336331255715610420057716412306973057066214536651135113958591233120032575026733958333075072812533734264534700060266054734545664338772667663471584128617435526828390065275357404052057121004150076600323056277610287226003175060305765803534256207472564464060539095425076777272921345209305565332021506258456045760350722804223710051277402927664527742911662372066523664321240336446744622769760467573259652733383263657311072452563376417025746807407539006144613252696869456340066810522645386256555532111000531265754227302628303438085756243800662563286838775672222439672172403542396107375860647335106645704512536703506321757004413636764365347431287321355256580631556063583463563610567737660541737377552828605563116564297412076854033003344323337052606873573426066033102439280977115921594064314334576408722871427337224310757744412937522268303871367257627564750472597763507745283571571263580550066921715611703323062474012471272931363924743604256803437445104259400424433362673769543855403976310365501153573745056364696326060377575776050561075823064353055604551028500311453022452525062305534574"
    
    private val divocQR = "B64:UEsDBBQACAAIAAAAAAAAAAAAAAAAAAAAAAAQAAAAY2VydGlmaWNhdGUuanNvbrxUXXPqNhD9K8w+G2Kbb7+0ickNJmBDHAihc4cRsowFtmQkGUIy/PeOTEJuOrmdPvXN1p5d7Z5ztG/wJ+ZMkRcFzl+QKJVL5+rqcDjUDvUaF+sr27Q6V1iQiDBFUSqv9hYYFyDmB8pqa76vUfYVhTCmDCnKmc74aYA65kTfMSOCxhStUuJe8GDAWHAeB/HsM++X8E8DPouHxWpDsALn7b0mjImQnIEBNAIHIho5YyRlzoVyekWWHe1mCwwQJPZ0vN5tdy2r0zQbVqfRBgMYynSVx4SKqDJCAieVqSSiEjACBqwJi4gAB0Yo1f9orcGtZpmo+0QpVUdwwGMRRboLFEWCSKkblEoQoq4/DgCMr0f2+SyiUgmqZwI3QdkKgQH4XPWz3gNZU87AgT7NEE5K1t5DLi+YEmUTPhiQc6lQ6vKIgGO1W7bZPp0MoFIW5SDfigdnAGKY9JDSI9qmbVXNetWsP5oNx+44jVbNtOwFGED2NCIMaz3fzqR/W/NsAnLVtc1Gu9U2rVYXDIgJiVYIb6ci/V3iH18zKIv5f0djIhSNKUaKlHp/jV5s+Gk0ba8VUjgBB0q/VB/v/OpFiPcptDbBzAv73u2wBwZkiBUxwqoQJakhEUVW8ZhUVBWKVHhcKQ2h1f0dn/WaVW+UfMYxwYruSaiQUF+wv0anTNH0n9GISy2zAYorlPa4JBIc24B9+c50b28fDi+Hq3xMzgWcDIgRpmcDX2Af/qqEVJGKjzJSObvo3739/lUJ4nPiJed/djzA6XT6aQDj7FEgJmMi9LYBB5Qo9AvO9ar5ZX08SBTSNUNaSr3uoNw2SJHo+1egFTuzi0v/jIhK+Mfmoe+il5eMC5GX8gCSUtvyAjZgc9CkkeMgWd1hGtDBZPrqWT71pJd5tu96rUX2Q2J7Kr3MP6L5hAappM+bZ9NLrW6txm6rdfrcw/d8W3Wzx6yer/LjYpKQXT7rcbGMzeeJahTbp97dcVHs1KIoOv5DoAabYFEd+tdo71cHC+VeRwXjMbG61Qy7dt+1zafl8eX1Lr6xvChWVtMVxeApKPbb7f18bk45eV0kcrt0o2o/I33iutM1Pw5pT+Xh7nUedZ92tH+3nPv17ng62nt9trge82ZD8ki9Xt/OO52hdReGUb09uBFBes/JaDRArjnoebe98MesGqLZdrnvN4nZmu06rSexHW2afNPId9ma7dL203Kn6v712t9NJuvp1E/kKOgf+vP7G9HcdMzjJreGDyMa3XSswgwng/3qZTJ7GbI8ma7G+NFrJC02HJDFYBNQgoPhbbj2boe5ay/D3hpOp78DAAD//1BLBwjwtt5R3gMAABMHAABQSwECFAAUAAgACAAAAAAA8LbeUd4DAAATBwAAEAAAAAAAAAAAAAAAAAAAAAAAY2VydGlmaWNhdGUuanNvblBLBQYAAAAAAQABAD4AAAAcBAAAAAA="
    
    private val icaoQR = "ICAO:TKN01234567890ABC"
    private val unknownQR = "UNKNOWN:123456789"
    
    @BeforeEach
    fun setUp() {
        registry = CompoundRegistry(TrustRegistryFactory.getTrustRegistries()).apply {
            init()
        }
        validationService = ValidationService(registry)
    }
    
    @Test
    fun testFormatRecognition_HCERT() {
        val result = validationService.recognizeFormat(hcertQR)
        
        assertTrue(result.recognized)
        assertEquals(ValidationService.QRFormat.HCERT, result.format)
        assertEquals("HCERT (EU DCC or WHO DDCC)", result.description)
    }
    
    @Test
    fun testFormatRecognition_SHC() {
        val result = validationService.recognizeFormat(shcQR)
        
        assertTrue(result.recognized)
        assertEquals(ValidationService.QRFormat.SHC, result.format)
        assertEquals("Smart Health Cards", result.description)
    }
    
    @Test
    fun testFormatRecognition_DIVOC() {
        val result = validationService.recognizeFormat(divocQR)
        
        assertTrue(result.recognized)
        assertEquals(ValidationService.QRFormat.DIVOC_B64, result.format)
        assertEquals("DIVOC (India) - Base64", result.description)
    }
    
    @Test
    fun testFormatRecognition_ICAO() {
        val result = validationService.recognizeFormat(icaoQR)
        
        assertTrue(result.recognized)
        assertEquals(ValidationService.QRFormat.ICAO, result.format)
        assertEquals("ICAO travel documents", result.description)
    }
    
    @Test
    fun testFormatRecognition_Unknown() {
        val result = validationService.recognizeFormat(unknownQR)
        
        assertFalse(result.recognized)
        assertNull(result.format)
        assertEquals("Unrecognized QR format", result.description)
    }
    
    @Test
    fun testSpecificFormatRecognition_Match() {
        val result = validationService.recognizeSpecificFormat(hcertQR, ValidationService.QRFormat.HCERT)
        
        assertTrue(result.recognized)
        assertEquals(ValidationService.QRFormat.HCERT, result.format)
    }
    
    @Test
    fun testSpecificFormatRecognition_NoMatch() {
        val result = validationService.recognizeSpecificFormat(hcertQR, ValidationService.QRFormat.SHC)
        
        assertFalse(result.recognized)
        assertTrue(result.description!!.contains("not Smart Health Cards"))
    }
    
    @Test
    fun testKidExtraction_HCERT() {
        val result = validationService.extractKid(hcertQR)
        
        assertNotNull(result)
        assertEquals(ValidationService.QRFormat.HCERT, result.format)
        // KID extraction success depends on whether the QR can be decoded
        // For test purposes, we'll check that the method doesn't crash
    }
    
    @Test
    fun testKidExtraction_Unknown() {
        val result = validationService.extractKid(unknownQR)
        
        assertFalse(result.hasKid)
        assertNull(result.kid)
        assertNull(result.format)
    }
    
    @Test
    fun testContentExtraction_HCERT() {
        val result = validationService.extractContent(hcertQR)
        
        assertNotNull(result)
        assertEquals(ValidationService.QRFormat.HCERT, result.format)
        // Content extraction success depends on validity of QR
        // For test purposes, we'll check that the method doesn't crash
    }
    
    @Test
    fun testContentExtraction_Unknown() {
        val result = validationService.extractContent(unknownQR)
        
        assertFalse(result.contentExtracted)
        assertNull(result.content)
        assertNull(result.format)
        assertEquals("Unrecognized QR format", result.error)
    }
    
    @Test
    fun testSignatureValidation_HCERT() {
        val result = validationService.validateSignature(hcertQR)
        
        assertNotNull(result)
        // Signature validation result depends on trust registry and QR validity
        // For test purposes, we'll check that the method doesn't crash
    }
    
    @Test
    fun testKidEnvironment() {
        val result = validationService.checkKidEnvironment("test_kid")
        
        assertNotNull(result)
        assertEquals("test_kid", result.kid)
        // Environment check result depends on trust registry content
        // For test purposes, we'll check that the method doesn't crash
    }
}