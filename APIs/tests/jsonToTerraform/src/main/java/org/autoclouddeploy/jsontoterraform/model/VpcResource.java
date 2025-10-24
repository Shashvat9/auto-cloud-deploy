package org.autoclouddeploy.jsontoterraform.model;

import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete POJO representing an AWS VPC (aws_vpc).
 * Inherits fields like uniqueId, resourceType, arguments, and terraformOutputs from CloudResource.
 * Specific fields relevant only to VPC could be added here if needed for processing
 * *before* HCL generation, but typically arguments are handled via the JsonNode.
 */
@NoArgsConstructor // Needed for Jackson deserialization
@ToString(callSuper = true) // Include fields from CloudResource in toString()
public class VpcResource extends CloudResource {
    // No VPC-specific fields needed here currently,
    // as all configuration ('cidr_block', 'tags', etc.)
    // is expected within the 'arguments' JsonNode inherited from CloudResource.
    @Override
    public String getResourceTypeString() {
        return "aws_vpc";
    }
}
