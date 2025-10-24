package org.autoclouddeploy.jsontoterraform.model;


import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete POJO representing an AWS Subnet (aws_subnet).
 * Inherits fields like uniqueId, resourceType, arguments, and terraformOutputs from CloudResource.
 */
@NoArgsConstructor // Needed for Jackson deserialization
@ToString(callSuper = true) // Include fields from CloudResource in toString()
public class SubnetResource extends CloudResource {
    // No Subnet-specific fields needed here currently,
    // as all configuration ('cidr_block', 'vpc_id_ref', 'availability_zone', 'tags', etc.)
    // is expected within the 'arguments' JsonNode inherited from CloudResource.
    @Override
    public String getResourceTypeString() {
        return "aws_subnet";
    }
}