package za.co.handyflow.platform.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI handyFlowOpenAPI() {
        /*
         * WHY define a security scheme here?
         * This tells Swagger UI to show an "Authorize" button.
         * You paste your JWT token there once, and Swagger
         * automatically adds "Authorization: Bearer <token>"
         * to every subsequent request. No manual header typing.
         */
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("HandyFlow Platform API")
                        .description("""
                    ## HandyFlow Business Operating System
                    
                    Modular Multi-Tenant SaaS Platform.
                    
                    ### Authentication
                    1. Call `POST /api/v1/auth/register` to create a company + admin account
                    2. Call `POST /api/v1/auth/login` to get your JWT token
                    3. Click **Authorize** and paste the token (without "Bearer " prefix)
                    4. All subsequent requests will be authenticated automatically
                    """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("HandyFlow Support")
                                .email("support@handyflow.co.za")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token here")));
    }
}
