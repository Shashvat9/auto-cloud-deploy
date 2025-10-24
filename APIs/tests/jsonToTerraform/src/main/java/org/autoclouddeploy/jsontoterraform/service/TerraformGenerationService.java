package org.autoclouddeploy.jsontoterraform.service; // <<< CORRECT PACKAGE

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.autoclouddeploy.jsontoterraform.generation.TerraformResourceGenerator; // <<< CORRECT PACKAGE
import org.autoclouddeploy.jsontoterraform.model.CloudResource; // <<< CORRECT PACKAGE
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the generation of Terraform HCL code from a list of CloudResource objects.
 * Uses a map of TerraformResourceGenerator strategies to handle different resource types.
 * Implements a two-pass approach:
 * 1. Populate Terraform output references for all resources.
 * 2. Generate HCL for each resource, resolving dependencies using the populated references.
 */
@Service
public class TerraformGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TerraformGenerationService.class);
    private final ObjectMapper objectMapper; // Keep ObjectMapper if needed elsewhere or by generators
    private final Map<String, TerraformResourceGenerator> generators;

    // Registry to hold CloudResource objects for the current request, keyed by uniqueId.
    // Cleared for each new request to ensure statelessness.
    private final Map<String, CloudResource> resourceRegistry = new HashMap<>();

    /**
     * Injects the ObjectMapper and all discovered TerraformResourceGenerator beans.
     * Builds a map of generators keyed by their resource type string (e.g., "aws_vpc").
     *
     * @param objectMapper Jackson ObjectMapper for JSON processing.
     * @param generatorList List of all beans implementing TerraformResourceGenerator, injected by Spring.
     */
    @Autowired
    public TerraformGenerationService(ObjectMapper objectMapper, List<TerraformResourceGenerator> generatorList) {
        this.objectMapper = objectMapper;
        // Create a map where the key is the resource type (e.g., "aws_vpc")
        // and the value is the generator instance that handles it.
        this.generators = generatorList.stream()
                .collect(Collectors.toMap(TerraformResourceGenerator::getResourceType, Function.identity()));
        log.info("Loaded Terraform Generators: {}", this.generators.keySet()); // Log loaded generators
    }


    /**
     * Generates Terraform HCL code from a list of CloudResource objects.
     * This version accepts the pre-parsed list directly.
     *
     * @param provider The cloud provider (e.g., "aws").
     * @param region   The target region (e.g., "us-east-1").
     * @param resources The list of CloudResource objects created by DiagramParserService.
     * @return A string containing the generated Terraform HCL code.
     * @throws IllegalArgumentException If required data (like uniqueId) is missing or generators are not found.
     */
    public String generate(String provider, String region, List<CloudResource> resources) throws IllegalArgumentException {
        // Clear the registry for the current request
        resourceRegistry.clear();
        log.info("Starting HCL generation for {} resources in region {} for provider {}.", resources.size(), region, provider);


        // --- Populate Registry & Validate ---
        if (resources == null || resources.isEmpty()) {
            log.warn("Resource list is null or empty. Returning empty HCL.");
            return ""; // Or provider block only?
        }

        for (CloudResource resource : resources) {
            if (resource.getUniqueId() == null || resource.getUniqueId().trim().isEmpty()) {
                // This validation should ideally happen in DiagramParserService, but double-check here.
                log.error("Resource is missing required 'uniqueId'. Resource details: {}", resource);
                throw new IllegalArgumentException("Resource is missing required 'uniqueId'.");
            }
            if (resourceRegistry.containsKey(resource.getUniqueId())) {
                log.error("Duplicate uniqueId found: {}", resource.getUniqueId());
                throw new IllegalArgumentException("Duplicate uniqueId found: " + resource.getUniqueId());
            }
            // Get the resource type using Jackson's understanding based on @JsonTypeInfo
            // Note: This relies on the object already being the correct subtype (VpcResource, etc.)
            String resourceType = objectMapper.convertValue(resource, JsonNode.class).path("resource_type").asText(null);
            if (resourceType == null || resourceType.isBlank()) {
                log.error("Could not determine resource_type for uniqueId: {}. Object: {}", resource.getUniqueId(), resource);
                throw new IllegalArgumentException("Could not determine resource_type for uniqueId: " + resource.getUniqueId());
            }
            log.debug("Registering resource: {} (Type detected: {})", resource.getUniqueId(), resourceType);
            resourceRegistry.put(resource.getUniqueId(), resource);
        }
        log.info("Resource registry populated with {} items.", resourceRegistry.size());


        // --- Pass 1: Populate Outputs ---
        // Iterate through all resources and have their generators calculate
        // and store their output reference syntax (e.g., "aws_vpc.my_vpc_tf.id").
        log.info("Starting Pass 1: Populate Outputs...");
        for (CloudResource resource : resourceRegistry.values()) {
            // Re-detect type for safety, although should be consistent
            String resourceType = objectMapper.convertValue(resource, JsonNode.class).path("resource_type").asText(null);
            TerraformResourceGenerator generator = generators.get(resourceType);
            if (generator != null) {
                log.debug("Populating outputs for {} ({})", resource.getUniqueId(), resourceType);
                generator.populateOutputs(resource, this); // Pass 'this' service instance
            } else {
                log.warn("Warning: No generator found for resource type: {} (uniqueId: {}). Skipping output population.", resourceType, resource.getUniqueId());
                // Optionally throw an exception here if all types must be supported
                // throw new IllegalArgumentException("No generator found for resource type: " + resource.getResourceType());
            }
        }
        log.info("Finished Pass 1.");

        // --- Pass 2: Generate HCL ---
        log.info("Starting Pass 2: Generate HCL...");
        StringBuilder finalHcl = new StringBuilder();

        // Add provider block (customize as needed)
        finalHcl.append(generateProviderBlock(provider, region));
        finalHcl.append("\n\n");

        // Iterate again to generate HCL, now with output references populated.
        for (CloudResource resource : resourceRegistry.values()) {
            // Re-detect type
            String resourceType = objectMapper.convertValue(resource, JsonNode.class).path("resource_type").asText(null);
            TerraformResourceGenerator generator = generators.get(resourceType);
            if (generator != null) {
                try {
                    log.debug("Generating HCL for {} ({})", resource.getUniqueId(), resourceType);
                    // Pass the registry so generators can resolve dependencies
                    finalHcl.append(generator.generateHcl(resource, resourceRegistry, this));
                    finalHcl.append("\n\n"); // Add blank lines between resources
                } catch (Exception e) {
                    log.error("Error generating HCL for resource {}: {}", resource.getUniqueId(), e.getMessage(), e);
                    // Append an error comment to the HCL output for easier debugging
                    finalHcl.append(String.format("# ERROR generating resource %s (%s): %s\n\n",
                            resource.getUniqueId(), resourceType, e.getMessage()));
                    // Depending on requirements, you might want to re-throw the exception
                    // throw new RuntimeException("Error generating HCL for resource " + resource.getUniqueId(), e);
                }
            } else {
                // Handle resources with no generator found during HCL generation phase
                log.error("ERROR: No generator found during HCL generation for resource type {} (uniqueId: {})",
                        resourceType, resource.getUniqueId());
                finalHcl.append(String.format("# ERROR: No generator found for resource type %s (uniqueId: %s)\n\n",
                        resourceType, resource.getUniqueId()));
            }
        }
        log.info("Finished Pass 2.");

        return finalHcl.toString();
    }


    /**
     * Legacy generate method accepting JSON string - kept for direct testing endpoint /generate
     * DEPRECATED for /generate-from-diagram flow.
     * @param instructionJson JSON string with provider, region, resources.
     * @return HCL string.
     * @throws JsonProcessingException On JSON parse error.
     * @throws IllegalArgumentException On validation errors.
     */
    public String generate(String instructionJson) throws JsonProcessingException, IllegalArgumentException {
        log.warn("Calling legacy generate(String instructionJson) method. Consider using generate(provider, region, List<CloudResource>)");
        // --- Parse Input JSON ---
        JsonNode rootNode = objectMapper.readTree(instructionJson);
        String provider = rootNode.path("provider").asText("aws"); // Default to aws
        String region = rootNode.path("region").asText(); // Region might be optional

        if (!rootNode.has("resources") || !rootNode.path("resources").isArray()) {
            throw new IllegalArgumentException("Input JSON must contain a 'resources' array.");
        }

        // Use TypeReference to deserialize the list of polymorphic CloudResource objects
        // THIS IS THE LINE THAT WAS FAILING
        List<CloudResource> resources;
        try {
            resources = objectMapper.convertValue(
                    rootNode.path("resources"),
                    new TypeReference<List<CloudResource>>() {}
            );
        } catch (IllegalArgumentException e) {
            log.error("Failed to deserialize resources list from JSON string: {}", e.getMessage(), e);
            // Re-throw with more context if needed, or handle differently
            throw new IllegalArgumentException("Error deserializing 'resources' list: " + e.getMessage(), e);
        }


        // Call the primary generate method
        return generate(provider, region, resources);
    }



    /**
     * Generates a Terraform-compatible local name from the resource's uniqueId.
     * Replaces hyphens with underscores and appends "_tf" to avoid potential conflicts
     * with Terraform keywords or other identifiers.
     * ENSURE THIS METHOD IS PUBLIC.
     *
     * @param resource The CloudResource object.
     * @return A safe string to use as the Terraform local resource name.
     */
    public String generateLocalName(CloudResource resource) { // <<< MUST BE PUBLIC
        if (resource == null || resource.getUniqueId() == null) {
            return "invalid_resource_tf"; // Fallback
        }
        // Replace invalid characters (like hyphens) with underscores
        // Also ensure it starts with a letter or underscore if necessary (Terraform requirement)
        String safeId = resource.getUniqueId().replaceAll("[^a-zA-Z0-9_\\-]", "_").replace('-', '_');
        // Ensure it doesn't start with a number
        if (safeId.length() > 0 && Character.isDigit(safeId.charAt(0))) {
            safeId = "_" + safeId;
        }
        // Ensure it starts with a letter or underscore (covers empty string case implicitly)
        else if (safeId.isEmpty() || (!Character.isLetter(safeId.charAt(0)) && safeId.charAt(0) != '_') ) {
            safeId = "_" + safeId; // Prepend underscore if starts with other invalid char
        }
        return safeId + "_tf"; // Append suffix for clarity
    }

    /**
     * Generates the Terraform provider block.
     *
     * @param provider The cloud provider name (e.g., "aws").
     * @param region   The target region string.
     * @return The HCL string for the provider block.
     */
    private String generateProviderBlock(String provider, String region) {
        StringBuilder sb = new StringBuilder();
        // Use default provider if null/blank
        String effectiveProvider = (provider != null && !provider.isBlank()) ? provider : "aws";
        sb.append(String.format("provider \"%s\" {\n", effectiveProvider));
        if (region != null && !region.trim().isEmpty()) {
            sb.append(String.format("  region = \"%s\"\n", region));
        }
        // Add other provider configurations if needed (e.g., credentials, aliases)
        sb.append("}\n");
        return sb.toString();
    }

    // --- Added Getter for Registry (might be useful for testing or complex scenarios) ---
    /**
     * Provides read-only access to the current request's resource registry.
     * Primarily for use by generators needing to look up dependencies.
     * @return An unmodifiable view of the resource registry map.
     */
    public Map<String, CloudResource> getResourceRegistry() {
        return Collections.unmodifiableMap(resourceRegistry);
    }
}