package org.who.gdhcnvalidator.web

import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer

class ServletInitializer : SpringBootServletInitializer() {
    override fun configure(builder: SpringApplicationBuilder): SpringApplicationBuilder {
        return builder.sources(
            WebApplication::class.java,
            JacksonConfig::class.java,
            UIController::class.java,
            GdhcnRestController::class.java   // renamed controller
        )
    }
}
