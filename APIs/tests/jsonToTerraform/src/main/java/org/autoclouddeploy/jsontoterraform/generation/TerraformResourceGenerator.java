package org.autoclouddeploy.jsontoterraform.generation; // Correct package

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.autoclouddeploy.jsontoterraform.model.CloudResource; // Correct package
import org.autoclouddeploy.jsontoterraform.service.TerraformGenerationService; // Correct package

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Interface defining the contract for generating Terraform HCL for a specific resource type.
 * Implementations of this interface handle the logic for one resource (e.g., aws_vpc, aws_subnet).
 */
public interface TerraformResourceGenerator {

    /**
     * Gets the Terraform resource type string this generator handles (e.g., "aws_vpc").
     * @return The resource type string.
     */
    String getResourceType();

    /**
     * Populates the standard Terraform outputs for this resource type into the CloudResource object.
     * Called during Pass 1.
     * @param resource The CloudResource object to populate.
     * @param service  The generation service instance (needed for utility methods like generateLocalName).
     */
    void populateOutputs(CloudResource resource, TerraformGenerationService service);

    /**
     * Generates the HCL block for the specific resource. Called during Pass 2.
     * @param resource        The CloudResource object containing arguments.
     * @param resourceRegistry Map of all resources for dependency resolution.
     * @param service         The generation service instance (needed for utility methods).
     * @return The generated HCL string for this resource.
     * @throws IllegalArgumentException if required arguments are missing or invalid.
     */
    String generateHcl(CloudResource resource, Map<String, CloudResource> resourceRegistry, TerraformGenerationService service);


    // --- Default Helper Methods ---

    /**
     * Helper to safely get a String argument from the JsonNode, providing a default value if missing or null.
     * @param argumentsNode The JsonNode containing resource arguments.
     * @param key           The argument key (e.g., "cidr_block").
     * @param defaultValue  The default value to return if the key is missing, null, or not text.
     * @return The argument value or the default value.
     */
    default String getStringArgument(JsonNode argumentsNode, String key, String defaultValue) {
        if (argumentsNode != null && argumentsNode.hasNonNull(key) && argumentsNode.get(key).isTextual()) {
            String value = argumentsNode.get(key).asText();
            return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
        }
        return defaultValue;
    }

    /**
     * Helper to safely get a Boolean argument from the JsonNode, providing a default value.
     * @param argumentsNode The JsonNode containing resource arguments.
     * @param key           The argument key (e.g., "enable_dns_support").
     * @param defaultValue  The default boolean value.
     * @return The argument value or the default value.
     */
    default boolean getBooleanArgument(JsonNode argumentsNode, String key, boolean defaultValue) {
        if (argumentsNode != null && argumentsNode.hasNonNull(key) && argumentsNode.get(key).isBoolean()) {
            return argumentsNode.get(key).asBoolean();
        }
        // Allow string "true"/"false" as well? Add logic here if needed.
        // else if (argumentsNode != null && argumentsNode.hasNonNull(key) && argumentsNode.get(key).isTextual()) {
        //     String textValue = argumentsNode.get(key).asText().toLowerCase();
        //     if ("true".equals(textValue)) return true;
        //     if ("false".equals(textValue)) return false;
        // }
        return defaultValue;
    }

    /**
     * Helper to safely get an Integer argument from the JsonNode, providing a default value.
     * @param argumentsNode The JsonNode containing resource arguments.
     * @param key           The argument key (e.g., "size").
     * @param defaultValue  The default integer value.
     * @return The argument value or the default value.
     */
    default int getIntArgument(JsonNode argumentsNode, String key, int defaultValue) {
        if (argumentsNode != null && argumentsNode.hasNonNull(key) && argumentsNode.get(key).isInt()) {
            return argumentsNode.get(key).asInt();
        }
        // Allow numeric strings?
        else if (argumentsNode != null && argumentsNode.hasNonNull(key) && argumentsNode.get(key).isTextual()) {
            try {
                return Integer.parseInt(argumentsNode.get(key).asText());
            } catch (NumberFormatException e) {
                // Ignore if parsing fails, return default
            }
        }
        return defaultValue;
    }

    /**
     * Helper to safely get a List<String> argument from the JsonNode.
     * Returns an empty list if missing, null, or not an array.
     * @param argumentsNode The JsonNode containing resource arguments.
     * @param key           The argument key (e.g., "subnet_ids").
     * @return The List of Strings, or an empty List.
     */
    default List<String> getStringListArgument(JsonNode argumentsNode, String key) {
        List<String> list = new ArrayList<>();
        if (argumentsNode != null && argumentsNode.hasNonNull(key) && argumentsNode.get(key).isArray()) {
            argumentsNode.get(key).forEach(node -> {
                if (node.isTextual()) {
                    list.add(node.asText());
                }
            });
        }
        return list;
    }


    /**
     * Generates HCL lines for simple arguments (String, Boolean, Number).
     * @param argumentsNode The JsonNode containing resource arguments.
     * @param indent        Indentation string (e.g., "  ").
     * @param simpleArgsSet Set of argument keys considered simple.
     * @return HCL string snippet for simple arguments.
     */
    default String generateSimpleArgumentsBlock(JsonNode argumentsNode, String indent, Set<String> simpleArgsSet) {
        StringBuilder sb = new StringBuilder();
        if (argumentsNode != null && argumentsNode.isObject()) {
            argumentsNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                // Only include if it's a defined simple arg and not null
                if (simpleArgsSet.contains(key) && valueNode != null && !valueNode.isNull()) {
                    String terraformKey = key; // Usually the same, but could transform if needed
                    String terraformValue = formatArgumentValue(valueNode);
                    if (terraformValue != null) { // Only append if formatting was successful
                        sb.append(String.format("%s%s = %s\n", indent, terraformKey, terraformValue));
                    }
                }
            });
        }
        return sb.toString();
    }

    /**
     * Formats a JsonNode value into its HCL representation (string, boolean, number).
     * Handles basic types. Returns null for unsupported types or null nodes.
     * @param valueNode The JsonNode value.
     * @return HCL formatted string (e.g., "\"value\"", "true", "123") or null.
     */
    default String formatArgumentValue(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null; // Don't output null values unless explicitly handled
        }
        return switch (valueNode.getNodeType()) {
            case STRING -> String.format("\"%s\"", valueNode.asText().replace("\"", "\\\"")); // Escape quotes
            case BOOLEAN -> String.valueOf(valueNode.asBoolean());
            case NUMBER -> String.valueOf(valueNode.numberValue()); // Handles int, long, double
            // Add ARRAY, OBJECT handling if needed for simple inline lists/maps, though complex blocks are usually handled separately
            default -> null; // Ignore other types by default in this simple helper
        };
    }


    /**
     * Generates the HCL `tags = { ... }` block from a 'tags' object within the arguments.
     * @param argumentsNode The JsonNode containing resource arguments.
     * @param indent        Indentation string (e.g., "  ").
     * @return HCL string snippet for the tags block, or empty string if no tags found.
     */
    default String generateTagsBlock(JsonNode argumentsNode, String indent) {
        StringBuilder sb = new StringBuilder();
        JsonNode tagsNode = (argumentsNode != null) ? argumentsNode.path("tags") : null;

        if (tagsNode != null && tagsNode.isObject() && tagsNode.size() > 0) {
            sb.append(indent).append("tags = {\n");
            tagsNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                if (valueNode.isTextual()) { // Only include string tags
                    sb.append(String.format("%s  \"%s\" = \"%s\"\n",
                            indent,
                            key.replace("\"", "\\\""),
                            valueNode.asText().replace("\"", "\\\"")
                    ));
                } else {
                    System.err.println("Warning: Ignoring non-string tag '" + key + "' in resource generation.");
                }
            });
            sb.append(indent).append("}\n");
        }
        return sb.toString();
    }

    /**
     * Resolves a dependency reference (e.g., "vpc_id_ref": "my-vpc") to its
     * corresponding Terraform output reference (e.g., "aws_vpc.my_vpc_tf.id").
     * @param argumentsNode    The arguments JsonNode for the *current* resource.
     * @param referenceKey     The key holding the uniqueId of the dependency (e.g., "vpc_id_ref").
     * @param resourceRegistry The map of all parsed CloudResource objects.
     * @param targetAttribute  The desired output attribute from the dependency (e.g., ".id", ".arn").
     * @param isRequired       Whether this dependency is mandatory.
     * @return The Terraform reference string (e.g., "aws_vpc.my_vpc_tf.id").
     * @throws IllegalArgumentException if required and the reference cannot be resolved.
     */
    default String resolveReference(JsonNode argumentsNode, String referenceKey, Map<String, CloudResource> resourceRegistry, String targetAttribute, boolean isRequired) {
        if (argumentsNode == null || !argumentsNode.hasNonNull(referenceKey) || !argumentsNode.get(referenceKey).isTextual()) {
            if (isRequired) {
                throw new IllegalArgumentException("Missing or invalid required reference key: " + referenceKey);
            }
            return null; // Optional reference not provided
        }

        String dependencyUniqueId = argumentsNode.get(referenceKey).asText();
        CloudResource dependency = resourceRegistry.get(dependencyUniqueId);

        if (dependency == null) {
            if (isRequired) {
                throw new IllegalArgumentException("Could not find referenced resource with uniqueId: " + dependencyUniqueId + " for key: " + referenceKey);
            }
            return null; // Optional reference points to non-existent resource? Log warning?
        }

        String outputReference = dependency.getTerraformOutput(targetAttribute);
        if (outputReference == null) {
            if (isRequired) {
                // This indicates an issue in populateOutputs for the dependency or wrong targetAttribute requested
                throw new IllegalArgumentException("Could not find target attribute '" + targetAttribute + "' in outputs for referenced resource: " + dependencyUniqueId);
            }
            return null;
        }

        return outputReference;
    }


    /**
     * Resolves a list of dependency references (e.g., "subnet_ids_ref": ["sub1", "sub2"])
     * to a list of corresponding Terraform output references (e.g., ["aws_subnet.sub1_tf.id", "aws_subnet.sub2_tf.id"]).
     * @param argumentsNode    The arguments JsonNode for the *current* resource.
     * @param referenceListKey The key holding the list of uniqueIds of the dependencies (e.g., "subnet_ids_ref").
     * @param resourceRegistry The map of all parsed CloudResource objects.
     * @param targetAttribute  The desired output attribute from each dependency (e.g., ".id").
     * @param isRequired       Whether this list dependency is mandatory (at least one valid ref required if true).
     * @return A List of Terraform reference strings (e.g., ["aws_subnet.sub1_tf.id", "aws_subnet.sub2_tf.id"]). Returns empty list if optional and not found/empty.
     * @throws IllegalArgumentException if required and the reference list is missing, empty, or contains unresolvable references.
     */
    default List<String> resolveReferenceList(JsonNode argumentsNode, String referenceListKey, Map<String, CloudResource> resourceRegistry, String targetAttribute, boolean isRequired) {
        List<String> resolvedReferences = new ArrayList<>();
        if (argumentsNode == null || !argumentsNode.hasNonNull(referenceListKey) || !argumentsNode.get(referenceListKey).isArray()) {
            if (isRequired) {
                throw new IllegalArgumentException("Missing or invalid required reference list key: " + referenceListKey);
            }
            return resolvedReferences; // Return empty list for optional, missing list
        }

        JsonNode refArray = argumentsNode.get(referenceListKey);
        if (isRequired && refArray.isEmpty()) {
            throw new IllegalArgumentException("Required reference list key '" + referenceListKey + "' cannot be empty.");
        }

        for (JsonNode refNode : refArray) {
            if (!refNode.isTextual()) {
                System.err.println("Warning: Non-textual value found in reference list for key: " + referenceListKey + ". Skipping.");
                continue; // Skip non-string references in the list
            }
            String dependencyUniqueId = refNode.asText();
            CloudResource dependency = resourceRegistry.get(dependencyUniqueId);

            if (dependency == null) {
                if(isRequired) {
                    // If the list itself is required, usually *all* its references must resolve
                    throw new IllegalArgumentException("Could not find referenced resource with uniqueId: " + dependencyUniqueId + " from list key: " + referenceListKey);
                } else {
                    System.err.println("Warning: Could not find referenced resource with uniqueId: " + dependencyUniqueId + " from list key: " + referenceListKey + ". Skipping.");
                    continue; // Skip if optional and reference is bad
                }
            }

            String outputReference = dependency.getTerraformOutput(targetAttribute);
            if (outputReference == null) {
                if(isRequired) {
                    throw new IllegalArgumentException("Could not find target attribute '" + targetAttribute + "' in outputs for referenced resource: " + dependencyUniqueId + " from list key: " + referenceListKey);
                } else {
                    System.err.println("Warning: Could not find target attribute '" + targetAttribute + "' in outputs for referenced resource: " + dependencyUniqueId + " from list key: " + referenceListKey + ". Skipping.");
                    continue; // Skip if optional and attribute missing
                }
            }
            resolvedReferences.add(outputReference);
        }
        // Final check if the list was required but ended up empty after skipping optional bad refs
        if (isRequired && resolvedReferences.isEmpty() && !refArray.isEmpty()) {
            throw new IllegalArgumentException("Required reference list key '" + referenceListKey + "' resulted in an empty list after resolving references.");
        }

        return resolvedReferences;
    }

    /**
     * Formats a list of resolved Terraform references into an HCL list string.
     * Example: ["aws_subnet.sub1_tf.id", "aws_subnet.sub2_tf.id"] -> "[aws_subnet.sub1_tf.id, aws_subnet.sub2_tf.id]"
     * @param references List of Terraform reference strings.
     * @return HCL list representation, or "[]" if the list is null or empty.
     */
    default String formatReferenceList(List<String> references) {
        if (references == null || references.isEmpty()) {
            return "[]";
        }
        // References are already strings like 'aws_subnet.sub1_tf.id', no extra quotes needed
        return "[" + String.join(", ", references) + "]";
    }

}

