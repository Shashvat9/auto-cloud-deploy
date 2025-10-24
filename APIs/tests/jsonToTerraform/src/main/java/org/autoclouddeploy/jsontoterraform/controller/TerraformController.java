package org.autoclouddeploy.jsontoterraform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.autoclouddeploy.jsontoterraform.model.CloudResource;
import org.autoclouddeploy.jsontoterraform.service.DiagramParserService;
import org.autoclouddeploy.jsontoterraform.service.TerraformGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/terraform") // Base path for Terraform related endpoints
public class TerraformController {

    private static final Logger log = LoggerFactory.getLogger(TerraformController.class);

    private final DiagramParserService diagramParserService;
    private final TerraformGenerationService terraformGenerationService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection for required services.
     * @param diagramParserService Service to parse diagram and form data.
     * @param terraformGenerationService Service to generate HCL from CloudResource objects.
     * @param objectMapper Jackson ObjectMapper for JSON handling.
     */
    @Autowired
    public TerraformController(DiagramParserService diagramParserService,
                               TerraformGenerationService terraformGenerationService,
                               ObjectMapper objectMapper) {
        this.diagramParserService = diagramParserService;
        this.terraformGenerationService = terraformGenerationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Accepts DIAGRAM JSON structure and corresponding FORM DATA (resource configurations
     * and dependencies identified by the frontend) to generate Terraform HCL.
     *
     * @param payload A JSON object containing:
     * - 'diagramStructureJson': The hierarchical JSON derived from the diagram XML.
     * - 'resourceConfigData': Map[uniqueId, Map[argName, argValue]] including 'resource_type'.
     * - 'dependencyData': Optional Map[uniqueId, Map[refArgName, targetUniqueId]].
     * @return ResponseEntity containing the generated Terraform HCL code (text/plain) or an error message.
     */
    @PostMapping(value = "/generate-from-diagram",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> generateTerraformFromDiagram(@RequestBody JsonNode payload) {

        log.info("Received request for /generate-from-diagram");
        JsonNode diagramJsonNode = payload.path("diagramStructureJson");
        JsonNode configDataNode = payload.path("resourceConfigData");
        JsonNode dependencyDataNode = payload.path("dependencyData");
        // --- Extract provider and region (Added) ---
        // Assuming they are top-level properties in the payload now, or get them from config?
        // Let's assume top-level for now. Provide defaults.
        String provider = payload.path("provider").asText("aws");
        String region = payload.path("region").asText("us-east-1"); // Example default


        // --- Input Validation ---
        if (diagramJsonNode.isMissingNode() || !diagramJsonNode.isObject()) {
            log.warn("Request rejected: Missing or invalid 'diagramStructureJson'");
            return ResponseEntity.badRequest().body("# ERROR: Missing or invalid 'diagramStructureJson' in request body.");
        }
        if (configDataNode.isMissingNode() || !configDataNode.isObject()) {
            log.warn("Request rejected: Missing or invalid 'resourceConfigData'");
            return ResponseEntity.badRequest().body("# ERROR: Missing or invalid 'resourceConfigData' object in request body.");
        }
        // dependencyDataNode is optional

        try {
            // --- Data Parsing ---
            log.debug("Parsing resourceConfigData and dependencyData from payload...");
            Map<String, Map<String, Object>> resourceConfigData = objectMapper.convertValue(configDataNode,
                    new TypeReference<Map<String, Map<String, Object>>>() {});

            Map<String, Map<String, String>> dependencyData = Map.of(); // Default to empty
            if (!dependencyDataNode.isMissingNode() && dependencyDataNode.isObject()){
                dependencyData = objectMapper.convertValue(dependencyDataNode,
                        new TypeReference<Map<String, Map<String, String>>>() {});
            }
            log.debug("Input data parsed successfully.");

            // --- Step 1: Create CloudResource List ---
            // Delegate the complex task of creating structured objects to the DiagramParserService
            log.info("Invoking DiagramParserService to create resource list...");
            List<CloudResource> resources = diagramParserService.createResourceList(
                    diagramJsonNode, // Pass diagram for context/validation if needed by the service
                    resourceConfigData,
                    dependencyData
            );
            log.info("DiagramParserService created list of {} resources.", resources.size());


            // --- Step 2: Generate HCL (Removed intermediate JSON step) ---
            // Pass the provider, region, and the list of objects directly
            log.info("Invoking TerraformGenerationService to generate HCL...");
            String hclOutput = terraformGenerationService.generate(provider, region, resources); // <<< CORRECTED CALL HERE
            log.info("Terraform HCL generation complete.");

            // --- Return Success Response ---
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN) // Set content type explicitly
                    .body(hclOutput);

        }
        // Keep JsonProcessingException for potential errors during input parsing
//        catch (JsonProcessingException e) {
//            log.error("JSON Processing Error during request input parsing: {}", e.getMessage(), e);
//            return ResponseEntity.badRequest().body("# ERROR: Invalid JSON input format.\n# Details: " + e.getMessage());
//        }
        catch (IllegalArgumentException e) {
            log.error("Data Validation or Generation Error: {}", e.getMessage(), e);
            // IllegalArgumentException can come from parser or generator
            return ResponseEntity.badRequest().body("# ERROR: Could not process input data or generate Terraform.\n# Details: " + e.getMessage());
        } catch (Exception e) {
            // Catch-all for unexpected errors
            log.error("Unexpected Internal Server Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("# ERROR: An internal server error occurred during generation.\n# Details: " + e.getMessage());
        }
    }

    /**
     * Alternate endpoint for testing the generation service directly with the
     * flat instruction JSON format (containing provider, region, and resources list).
     * Bypasses the DiagramParserService. Uses the legacy generate(String) method.
     *
     * @param instructionJson JSON string body with the flat structure.
     * @return ResponseEntity with HCL or error.
     */
    @PostMapping(value = "/generate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> generateTerraformFromJson(@RequestBody String instructionJson) {
        log.info("Received request for /generate (direct instruction JSON)");
        if (instructionJson == null || instructionJson.isBlank()) {
            log.warn("Request rejected: Empty request body for /generate");
            return ResponseEntity.badRequest().body("# ERROR: Request body is empty.");
        }
        log.trace("Direct instruction JSON payload: {}", instructionJson);

        try {
            // Directly call the legacy generation service method
            log.info("Invoking TerraformGenerationService directly with JSON string...");
            String hclOutput = terraformGenerationService.generate(instructionJson); // <<< USES LEGACY METHOD
            log.info("Direct HCL generation complete.");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(hclOutput);
        } catch (JsonProcessingException e) {
            log.error("JSON Parsing Error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("# ERROR: Invalid JSON input format.\n# Details: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Generation Error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("# ERROR: Could not generate Terraform.\n# Details: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected Error during Terraform generation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("# ERROR: An internal server error occurred during generation.\n# Details: " + e.getMessage());
        }
    }
}