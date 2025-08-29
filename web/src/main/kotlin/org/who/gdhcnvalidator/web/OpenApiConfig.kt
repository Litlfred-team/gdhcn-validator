package org.who.gdhcnvalidator.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("GDHCN Validator API")
                    .description("WHO Global Digital Health Certification Network (GDHCN) Validator - REST API for validating digital health certificates")
                    .version("0.1.0")
                    .contact(
                        Contact()
                            .name("WHO GDHCN Team")
                            .url("https://github.com/Litlfred-team/gdhcn-validator")
                    )
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0")
                    )
            )
            .addServersItem(
                Server()
                    .url("/")
                    .description("Local server")
            )
    }
}