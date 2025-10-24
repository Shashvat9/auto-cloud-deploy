package org.autoclouddeploy.jsontoterraform.generation;

import com.fasterxml.jackson.databind.JsonNode;
import org.autoclouddeploy.jsontoterraform.model.CloudResource;
import org.autoclouddeploy.jsontoterraform.service.TerraformGenerationService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component // Make this bean discoverable by Spring
public class SubnetGenerator implements TerraformResourceGenerator {

    private static final String RESOURCE_TYPE = "aws_subnet";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    /**
     * Populates the standard Terraform outputs for an aws_subnet resource.
     * Called during Pass 1.
     * @param resource The CloudResource object (SubnetResource) to populate.
     * @param service  The generation service instance.
     */
    @Override
    public void populateOutputs(CloudResource resource, TerraformGenerationService service) {
        String localName = service.generateLocalName(resource);
        String tfRefPrefix = RESOURCE_TYPE + "." + localName; // e.g., aws_subnet.my_subnet_tf

        // Standard Subnet Outputs (Add more if needed from TF docs)
        resource.setTerraformOutput(".id", tfRefPrefix + ".id");
        resource.setTerraformOutput(".arn", tfRefPrefix + ".arn");
        resource.setTerraformOutput(".vpc_id", tfRefPrefix + ".vpc_id");
        resource.setTerraformOutput(".cidr_block", tfRefPrefix + ".cidr_block");
        resource.setTerraformOutput(".availability_zone", tfRefPrefix + ".availability_zone");
        resource.setTerraformOutput(".availability_zone_id", tfRefPrefix + ".availability_zone_id");
        resource.setTerraformOutput(".owner_id", tfRefPrefix + ".owner_id");
    }

    /**
     * Generates the HCL block for the aws_subnet resource.
     * Called during Pass 2.
     * @param resource        The CloudResource object (SubnetResource) containing arguments.
     * @param resourceRegistry Map of all resources for dependency resolution.
     * @param service         The generation service instance.
     * @return The generated HCL string for this subnet resource.
     * @throws IllegalArgumentException if required arguments (cidr_block, vpc_id_ref) are missing or invalid.
     */
    @Override
    public String generateHcl(CloudResource resource, Map<String, CloudResource> resourceRegistry, TerraformGenerationService service) {
        JsonNode arguments = resource.getArguments();
        String localName = service.generateLocalName(resource);

        // --- Argument Validation & Retrieval ---
        String cidrBlock = getStringArgument(arguments, "cidr_block", null);
        if (cidrBlock == null || cidrBlock.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required argument 'cidr_block' for Subnet resource ID: " + resource.getUniqueId());
        }

        // Resolve VPC ID dependency
        String vpcIdRef = resolveReference(arguments, "vpc_id_ref", resourceRegistry, ".id", true);
        // resolveReference throws if required and not found, so vpcIdRef should be valid here.

        String availabilityZone = getStringArgument(arguments, "availability_zone", null);
        // Note: You might need availability_zone OR availability_zone_id depending on setup.
        // Add logic here if needed (e.g., require one or the other). Let's require AZ for now.
        if (availabilityZone == null || availabilityZone.trim().isEmpty()) {
            // Terraform often allows omitting AZ if using defaults or specific setups,
            // but let's make it required based on typical diagram input for simplicity.
            // You can make this optional later if needed.
            // throw new IllegalArgumentException("Missing required argument 'availability_zone' for Subnet resource ID: " + resource.getUniqueId());
            // OR default it? Let's skip outputting it if missing for now.
            availabilityZone = null; // Set to null explicitly if we decide not to throw error
        }


        // Optional arguments with defaults (Check AWS provider defaults)
        boolean mapPublicIpOnLaunch = getBooleanArgument(arguments, "map_public_ip_on_launch", false); // Common for public subnets
        // Add other optional arguments as needed (e.g., assign_ipv6_address_on_creation)


        // --- HCL Generation ---
        StringBuilder hcl = new StringBuilder();
        hcl.append(String.format("resource \"%s\" \"%s\" {\n", RESOURCE_TYPE, localName));

        // Required arguments
        hcl.append(String.format("  vpc_id     = %s\n", vpcIdRef)); // Use the resolved reference directly
        hcl.append(String.format("  cidr_block = \"%s\"\n", cidrBlock));

        // Conditionally add AZ if provided
        if (availabilityZone != null) {
            hcl.append(String.format("  availability_zone = \"%s\"\n", availabilityZone));
        }

        // Optional arguments
        hcl.append(String.format("  map_public_ip_on_launch = %b\n", mapPublicIpOnLaunch));
        // Add lines for other optional args here...


        // Tags
        hcl.append(TerraformResourceGenerator.super.generateTagsBlock(arguments, "  "));

        hcl.append("}\n");

        return hcl.toString();
    }
}