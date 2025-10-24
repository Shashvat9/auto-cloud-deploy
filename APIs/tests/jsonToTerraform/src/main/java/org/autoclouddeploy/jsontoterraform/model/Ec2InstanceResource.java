package org.autoclouddeploy.jsontoterraform.model;

import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete POJO representing an AWS EC2 Instance (aws_instance).
 * Inherits fields like uniqueId, resourceType, arguments, and terraformOutputs from CloudResource.
 */
@NoArgsConstructor // Needed for Jackson deserialization
@ToString(callSuper = true) // Include fields from CloudResource in toString()
public class Ec2InstanceResource extends CloudResource {
    @Override
    public String getResourceTypeString() {
        return "";
    }
    // No EC2-specific fields needed here currently,
    // as all configuration ('ami', 'instance_type', 'subnet_id_ref', 'tags', 'key_name', etc.)
    // is expected within the 'arguments' JsonNode inherited from CloudResource.
}

