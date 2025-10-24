package org.autoclouddeploy.jsontoterraform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.autoclouddeploy.jsontoterraform.model.CloudResource;
// Import specific resource types as they are created
import org.autoclouddeploy.jsontoterraform.model.Ec2InstanceResource;
import org.autoclouddeploy.jsontoterraform.model.SubnetResource;
import org.autoclouddeploy.jsontoterraform.model.VpcResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for parsing the diagram structure and form data
 * provided by the frontend into a list of CloudResource objects.
 */
@Service
public class DiagramParserService {

    private static final Logger log = LoggerFactory.getLogger(DiagramParserService.class);

    private final ObjectMapper objectMapper;

    @Autowired
    public DiagramParserService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a list of CloudResource objects based on configuration data provided
     * primarily by the frontend, potentially using the diagram for context or validation.
     *
     * @param diagramStructureJson The raw diagram structure (for context/validation, optional).
     * @param resourceConfigData   Map of uniqueId -> { resource_type: "...", arg1: "val1", ... } provided by frontend.
     * @param dependencyData       Map of uniqueId -> { arg_ref_name: "dependency_uniqueId", ... } provided by frontend.
     * @return A list of CloudResource POJOs ready for the TerraformGenerationService.
     * @throws IllegalArgumentException If required data is missing or inconsistent.
     */
    public List<CloudResource> createResourceList(
            JsonNode diagramStructureJson, // Keep for potential future use (validation, context)
            Map<String, Map<String, Object>> resourceConfigData,
            Map<String, Map<String, String>> dependencyData) throws IllegalArgumentException {

        log.info("Starting creation of CloudResource list from config data.");
        List<CloudResource> resources = new ArrayList<>();

        if (resourceConfigData == null || resourceConfigData.isEmpty()) {
            log.error("Validation failed: 'resourceConfigData' is null or empty.");
            throw new IllegalArgumentException("'resourceConfigData' cannot be null or empty.");
        }

        // Iterate through the configuration data provided by the frontend
        for (Map.Entry<String, Map<String, Object>> entry : resourceConfigData.entrySet()) {
            String uniqueId = entry.getKey();
            Map<String, Object> configArgs = entry.getValue();

            log.debug("Processing resource with uniqueId: {}", uniqueId);

            if (uniqueId == null || uniqueId.isBlank()) {
                log.error("Validation failed: Found resource configuration with missing or blank uniqueId.");
                throw new IllegalArgumentException("Resource configuration found with missing or blank uniqueId.");
            }
            if (configArgs == null) {
                log.error("Validation failed: Configuration arguments map is null for resource ID: {}", uniqueId);
                throw new IllegalArgumentException("Configuration arguments map is null for resource ID: " + uniqueId);
            }
            if (!configArgs.containsKey("resource_type") || !(configArgs.get("resource_type") instanceof String) || ((String)configArgs.get("resource_type")).isBlank()) {
                log.error("Validation failed: Missing, invalid, or blank 'resource_type' for resource ID: {}", uniqueId);
                throw new IllegalArgumentException("Missing, invalid, or blank 'resource_type' for resource ID: " + uniqueId);
            }


            String resourceType = (String) configArgs.get("resource_type");

            try {
                // Instantiate the correct CloudResource subclass
                CloudResource resource = instantiateResource(uniqueId, resourceType);

                // Build the arguments JsonNode, including dependencies
                ObjectNode argumentsNode = buildArgumentsNode(uniqueId, configArgs, dependencyData);
                resource.setArguments(argumentsNode);

                resources.add(resource);
                log.debug("Successfully created and added resource: {} (Type: {})", uniqueId, resourceType);

            } catch (IllegalArgumentException e) {
                // Log error from instantiateResource or buildArgumentsNode
                log.error("Skipping resource {}: {}", uniqueId, e.getMessage());
                // Optionally re-throw if you want the entire request to fail on any single resource error
                // throw e;
            } catch (Exception e) {
                // Catch unexpected errors during processing of this resource
                log.error("Unexpected error processing resource {}: {}", uniqueId, e.getMessage(), e);
                // Optionally re-throw
                // throw new RuntimeException("Error processing resource " + uniqueId, e);
            }
        }

        log.info("Finished creating CloudResource list. Total resources processed (including potential errors): {}", resourceConfigData.size());
        log.info("Successfully created CloudResource objects: {}", resources.size());


        // Optional: Add a validation step here.
        // E.g., check if all dependency references point to existing uniqueIds.
        validateDependencies(resources, resourceConfigData.keySet()); // Pass all defined IDs


        // Optional: Use diagramStructureJson for context-based validation if needed
        // validateAgainstDiagram(resources, diagramStructureJson);

        return resources;
    }

    /**
     * Instantiates the correct CloudResource subclass based on the resource type.
     */
    private CloudResource instantiateResource(String uniqueId, String resourceType) throws IllegalArgumentException {
        log.trace("Instantiating resource object for type: {} with uniqueId: {}", resourceType, uniqueId);
        CloudResource resource;
        // Use a switch or if-else chain based on resourceType
        switch (resourceType) {
            case "aws_vpc":
                resource = new VpcResource();
                break;
            case "aws_subnet":
                resource = new SubnetResource();
                break;
            case "aws_instance":
                resource = new Ec2InstanceResource();
                break;
            // Add cases for ALL supported resource types identified by the frontend
            // case "aws_eip":
            //     resource = new EipResource();
            //     break;
            // case "aws_lb": // Application Load Balancer
            //     resource = new AlbResource();
            //     break;
            // case "aws_internet_gateway":
            //     resource = new InternetGatewayResource();
            //     break;
            // case "aws_db_instance":
            //     resource = new RdsInstanceResource();
            //      break;
            // case "aws_autoscaling_group":
            //      resource = new AutoScalingGroupResource();
            //      break;
            default:
                log.error("Instantiation failed: Unsupported resource_type '{}' for ID '{}'", resourceType, uniqueId);
                throw new IllegalArgumentException("Unsupported resource_type: " + resourceType + " for ID: " + uniqueId);
        }
        resource.setUniqueId(uniqueId);
        // --- LINE REMOVED ---
        // resource.setResourceType(resourceType); // This line was removed as it's no longer needed.
        // --- LINE REMOVED ---
        log.trace("Instantiated {} for {}", resource.getClass().getSimpleName(), uniqueId);
        return resource;
    }

    /**
     * Builds the JsonNode for the 'arguments' field, merging config and dependency data.
     */
    private ObjectNode buildArgumentsNode(String uniqueId, Map<String, Object> configArgs, Map<String, Map<String, String>> dependencyData) {
        ObjectNode argumentsNode = objectMapper.createObjectNode();
        log.trace("Building arguments node for uniqueId: {}", uniqueId);

        // 1. Add direct configuration arguments from frontend/form
        for (Map.Entry<String, Object> argEntry : configArgs.entrySet()) {
            String key = argEntry.getKey();
            if (!"resource_type".equals(key)) { // Skip the type identifier itself
                try {
                    // Convert value to JsonNode to handle various types correctly
                    argumentsNode.set(key, objectMapper.valueToTree(argEntry.getValue()));
                    log.trace("Added config arg for {}: {} = {}", uniqueId, key, argumentsNode.get(key).toString());
                } catch (IllegalArgumentException e) {
                    log.warn("Could not convert value for argument '{}' in resource '{}'. Skipping. Value: {}", key, uniqueId, argEntry.getValue(), e);
                    // Consider if skipping is appropriate or if an error should be thrown
                }
            }
        }

        // 2. Add dependency references provided explicitly by frontend
        if (dependencyData != null && dependencyData.containsKey(uniqueId)) {
            Map<String, String> dependencies = dependencyData.get(uniqueId);
            log.trace("Adding explicit dependency references for {}", uniqueId);
            for (Map.Entry<String, String> depEntry : dependencies.entrySet()) {
                if(depEntry.getValue() != null && !depEntry.getValue().isBlank()){
                    argumentsNode.put(depEntry.getKey(), depEntry.getValue()); // Add e.g., "vpc_id_ref": "vpc-main"
                    log.trace("Added dependency ref for {}: {} = {}", uniqueId, depEntry.getKey(), depEntry.getValue());
                } else {
                    log.warn("Dependency reference value for key '{}' in resource '{}' is null or blank. Skipping.", depEntry.getKey(), uniqueId);
                }
            }
        } else {
            log.trace("No explicit dependency references found for {}", uniqueId);
        }

        return argumentsNode;
    }

    /**
     * Optional validation step: Check if all dependency references point to valid unique IDs
     * defined in the resourceConfigData.
     */
    private void validateDependencies(List<CloudResource> resources, java.util.Set<String> definedIds) {
        log.debug("Starting dependency validation...");
        boolean allDepsValid = true;
        for (CloudResource resource : resources) {
            JsonNode args = resource.getArguments();
            if (args != null && args.isObject()) {
                args.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    // Check fields matching the reference convention (e.g., ending with "_ref")
                    if (key.endsWith("_ref") && entry.getValue().isTextual()) {
                        String targetId = entry.getValue().asText();
                        if (!definedIds.contains(targetId)) {
                            log.error("Dependency Validation Error: Resource '{}' references '{}' via field '{}', but '{}' is not defined in resourceConfigData.",
                                    resource.getUniqueId(), targetId, key, targetId);
                            // Set flag or collect errors
                            // allDepsValid = false; // Uncomment to track validity
                        } else {
                            log.trace("Dependency validation OK: {} -> {} via {}", resource.getUniqueId(), targetId, key);
                        }
                    }
                });
            }
        }
        log.debug("Dependency validation finished.");
        // if (!allDepsValid) { // Uncomment to throw an exception if validation fails
        //     throw new IllegalArgumentException("One or more dependency references are invalid. Check logs for details.");
        // }
    }

    // Optional: Add validateAgainstDiagram method if needed later
    // private void validateAgainstDiagram(List<CloudResource> resources, JsonNode diagramStructureJson) { ... }
}