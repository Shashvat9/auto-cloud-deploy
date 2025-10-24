package org.autoclouddeploy.jsontoterraform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for all cloud resources defined in the input JSON.
 * Uses Jackson annotations for polymorphic deserialization based on the 'resource_type' field.
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore fields in JSON not present in the POJO
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,         // Use the value of a property to determine the type
        include = JsonTypeInfo.As.PROPERTY, // Include type info as a property in the JSON
        property = "resource_type",       // The name of the property holding the type identifier (e.g., "aws_vpc")
        visible = true                      // Make the 'resource_type' property accessible after deserialization
)
@JsonSubTypes({
        // Register all concrete subclasses here.
        // The 'name' must match the value expected in the 'resource_type' field from the frontend.
        @JsonSubTypes.Type(value = VpcResource.class, name = "aws_vpc"),
        @JsonSubTypes.Type(value = SubnetResource.class, name = "aws_subnet"),
        @JsonSubTypes.Type(value = Ec2InstanceResource.class, name = "aws_instance"),
        @JsonSubTypes.Type(value = EipResource.class, name = "aws_eip"),
        @JsonSubTypes.Type(value = AlbResource.class, name = "aws_lb"), // Assuming Application Load Balancer
        @JsonSubTypes.Type(value = InternetGatewayResource.class, name = "aws_internet_gateway"),
        @JsonSubTypes.Type(value = RdsInstanceResource.class, name = "aws_db_instance"), // Assuming RDS
        @JsonSubTypes.Type(value = AutoScalingGroupResource.class, name = "aws_autoscaling_group")
        // Add more @JsonSubTypes.Type entries as you implement new resource types
})
public abstract class CloudResource {

    /**
     * A unique identifier for this specific resource instance within the deployment request.
     * Provided by the frontend/DiagramParserService. Used for dependency resolution.
     * Example: "vpc-main", "subnet-private-1a"
     */
    private String uniqueId;

    // --- FIELD REMOVED ---
    // The 'private String resourceType;' field was here.
    // It conflicted with @JsonTypeInfo and has been removed.
    // --- FIELD REMOVED ---

    /**
     * Returns the Terraform resource type string associated with this class.
     * Used by the generation service to find the correct generator.
     * @return The Terraform resource type (e.g., "aws_vpc").
     */
    @JsonIgnore // Important: Don't let Jackson serialize this method as a field
    public abstract String getResourceTypeString();

    /**
     * A Jackson JsonNode representing the configuration arguments for this resource.
     * This includes both direct configuration values (from form data) and
     * dependency references (like "vpc_id_ref": "vpc-main").
     * Using JsonNode allows flexibility as different resources have vastly different arguments.
     * Set by DiagramParserService based on frontend input.
     */
    private JsonNode arguments;

    /**
     * Internal map to store the calculated Terraform output references for this resource.
     * Populated during Pass 1 of the generation process by the corresponding generator.
     * Key: Attribute name (e.g., ".id", ".arn", ".dns_name")
     * Value: Terraform reference string (e.g., "aws_vpc.my_vpc_tf.id")
     * Marked @JsonIgnore as this is internal state, not part of the input JSON.
     */
    @JsonIgnore // Exclude from JSON serialization/deserialization
    private final Map<String, String> terraformOutputs = new HashMap<>();

    // --- Helper methods for accessing outputs ---

    /**
     * Stores a calculated Terraform output reference. Called by generators during Pass 1.
     * @param attribute The attribute name (e.g., ".id").
     * @param terraformReference The Terraform HCL reference string (e.g., "aws_vpc.my_vpc_tf.id").
     */
    @JsonIgnore
    public void setTerraformOutput(String attribute, String terraformReference) {
        if (attribute != null && terraformReference != null) {
            this.terraformOutputs.put(attribute, terraformReference);
        }
    }

    /**
     * Retrieves a calculated Terraform output reference. Called by generators during Pass 2
     * when resolving dependencies.
     * @param attribute The attribute name (e.g., ".id").
     * @return The Terraform HCL reference string, or null if not found.
     */
    @JsonIgnore
    public String getTerraformOutput(String attribute) {
        return this.terraformOutputs.get(attribute);
    }

    /**
     * Retrieves the entire map of calculated Terraform output references.
     * @return The map of attribute names to reference strings.
     */
    @JsonIgnore
    public Map<String, String> getTerraformOutputs() {
        // Return a copy to prevent external modification
        return new HashMap<>(this.terraformOutputs);
    }

    // Abstract methods or common methods can be added here if needed
}