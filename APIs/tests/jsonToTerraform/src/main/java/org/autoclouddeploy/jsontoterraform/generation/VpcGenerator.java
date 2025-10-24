package org.autoclouddeploy.jsontoterraform.generation; // Ensure package name is correct

import com.fasterxml.jackson.databind.JsonNode;
// Ensure import paths are correct for your project structure
import org.autoclouddeploy.jsontoterraform.model.CloudResource;
import org.autoclouddeploy.jsontoterraform.service.TerraformGenerationService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Concrete implementation of TerraformResourceGenerator for the aws_vpc resource type.
 * Includes default values for optional arguments.
 */
@Component // Make this bean discoverable by Spring
public class VpcGenerator implements TerraformResourceGenerator {

    private static final String RESOURCE_TYPE = "aws_vpc";

    // REMOVED SIMPLE_ARGS set as we will handle arguments explicitly for defaults

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    /**
     * Populates the standard Terraform outputs for an aws_vpc resource into the CloudResource object.
     * These references (e.g., aws_vpc.my_vpc_tf.id) are used by other resources (like Subnets)
     * that depend on this VPC. This is called during Pass 1.
     *
     * @param resource The CloudResource object (specifically a VpcResource instance) to populate.
     * @param service  The generation service instance (needed for utility methods like generateLocalName).
     */
    @Override
    public void populateOutputs(CloudResource resource, TerraformGenerationService service) {
        String localName = service.generateLocalName(resource); // e.g., my_vpc_tf
        String tfRefPrefix = RESOURCE_TYPE + "." + localName;   // e.g., aws_vpc.my_vpc_tf

        // Calling the setTerraformOutput method defined in CloudResource.java
        resource.setTerraformOutput(".id", tfRefPrefix + ".id");
        resource.setTerraformOutput(".arn", tfRefPrefix + ".arn");
        resource.setTerraformOutput(".cidr_block", tfRefPrefix + ".cidr_block");
        resource.setTerraformOutput(".default_network_acl_id", tfRefPrefix + ".default_network_acl_id");
        resource.setTerraformOutput(".default_route_table_id", tfRefPrefix + ".default_route_table_id");
        resource.setTerraformOutput(".default_security_group_id", tfRefPrefix + ".default_security_group_id");
        resource.setTerraformOutput(".instance_tenancy", tfRefPrefix + ".instance_tenancy");
        resource.setTerraformOutput(".ipv6_association_id", tfRefPrefix + ".ipv6_association_id");
        resource.setTerraformOutput(".ipv6_cidr_block", tfRefPrefix + ".ipv6_cidr_block");
        resource.setTerraformOutput(".main_route_table_id", tfRefPrefix + ".main_route_table_id");
        resource.setTerraformOutput(".owner_id", tfRefPrefix + ".owner_id");
        // Add other relevant outputs as needed
    }

    /**
     * Generates the HCL block for the aws_vpc resource based on the provided arguments,
     * applying default values for optional parameters if they are missing.
     * This is called during Pass 2.
     *
     * @param resource        The CloudResource object (VpcResource) containing arguments.
     * @param resourceRegistry Map of all resources (not typically needed for VPC itself, but required by interface).
     * @param service         The generation service instance (needed for utility methods like generateLocalName).
     * @return The generated HCL string for this VPC resource.
     * @throws IllegalArgumentException if required arguments (like cidr_block) are missing.
     */
    @Override
    public String generateHcl(CloudResource resource, Map<String, CloudResource> resourceRegistry, TerraformGenerationService service) {
        JsonNode arguments = resource.getArguments();
        String localName = service.generateLocalName(resource); // e.g., my_vpc_tf

        // --- Basic Validation ---
        if (arguments == null) {
            // If arguments are entirely missing, provide defaults for everything possible
            // This assumes the DiagramParserService ensures a non-null but possibly empty JsonNode exists
            // For robustness, let's proceed assuming defaults might be needed even if node exists.
            // If arguments MUST exist, throw error here. Let's assume defaults are preferred.
            System.err.println("Warning: Arguments node is null or missing for VPC resource with uniqueId: " + resource.getUniqueId() + ". Proceeding with defaults.");
            // arguments = service.getObjectMapper().createObjectNode(); // Or create an empty node if needed, requires ObjectMapper injection/access
        }

        // --- Get Arguments with Defaults ---
        // Required argument - Check first, throw if missing
        String cidrBlock = getStringArgument(arguments, "cidr_block", null);
        if (cidrBlock == null || cidrBlock.trim().isEmpty()) {
            // Decide: Throw error or use a very default CIDR? Throwing is safer.
            throw new IllegalArgumentException("Missing required argument 'cidr_block' for VPC resource with uniqueId: " + resource.getUniqueId());
        }

        // Optional arguments - Use helper methods with default values
        // Check AWS provider defaults: https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/vpc#argument-reference
        String instanceTenancy = getStringArgument(arguments, "instance_tenancy", "default");
        boolean enableDnsSupport = getBooleanArgument(arguments, "enable_dns_support", true);
        // Note: Terraform's default for enable_dns_hostnames depends on enable_dns_support.
        // We'll apply the most common desired state: enable hostnames if support is enabled.
        boolean enableDnsHostnames = getBooleanArgument(arguments, "enable_dns_hostnames", enableDnsSupport); // Default to true *if* support is true
        boolean assignGeneratedIpv6CidrBlock = getBooleanArgument(arguments, "assign_generated_ipv6_cidr_block", false);
        // boolean enableClassiclink = getBooleanArgument(arguments, "enable_classiclink", false); // Deprecated? Check TF docs
        // boolean enableClassiclinkDnsSupport = getBooleanArgument(arguments, "enable_classiclink_dns_support", false); // Deprecated? Check TF docs


        // --- HCL Generation ---
        StringBuilder hcl = new StringBuilder();
        hcl.append(String.format("resource \"%s\" \"%s\" {\n", RESOURCE_TYPE, localName));

        // Required argument
        hcl.append(String.format("  cidr_block = \"%s\"\n", cidrBlock));

        // Optional arguments (now using the variables holding either the input value or the default)
        hcl.append(String.format("  instance_tenancy                 = \"%s\"\n", instanceTenancy));
        hcl.append(String.format("  enable_dns_support               = %b\n", enableDnsSupport));
        hcl.append(String.format("  enable_dns_hostnames             = %b\n", enableDnsHostnames));
        hcl.append(String.format("  assign_generated_ipv6_cidr_block = %b\n", assignGeneratedIpv6CidrBlock));
        // hcl.append(String.format("  enable_classiclink               = %b\n", enableClassiclink));
        // hcl.append(String.format("  enable_classiclink_dns_support = %b\n", enableClassiclinkDnsSupport));

        // You could potentially iterate through SIMPLE_ARGS still, but call the default helpers
        // This combines explicitness with some automation if the set is large.
        // Example (alternative to explicit calls above):
        // SIMPLE_ARGS.forEach(arg -> {
        //     if (!"cidr_block".equals(arg)) { // Skip required arg handled above
        //         JsonNode valueNode = arguments != null ? arguments.path(arg) : null;
        //         String hclValue = null;
        //         // Determine type and call appropriate default getter - more complex logic needed here
        //         // e.g., if (arg.equals("instance_tenancy")) hclValue = formatArgumentValue(objectMapper.getNodeFactory().textNode(getStringArgument(arguments, arg, "default")));
        //         // This shows why explicit calls are often clearer for defaults.
        //     }
        // });


        // --- Tags ---
        // Use the explicit default call method which handles missing/null tags node
        hcl.append(TerraformResourceGenerator.super.generateTagsBlock(arguments, "  "));

        // Close the resource block
        hcl.append("}\n");

        return hcl.toString();
    }
}

