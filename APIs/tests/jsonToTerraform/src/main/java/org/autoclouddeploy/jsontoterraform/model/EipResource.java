package org.autoclouddeploy.jsontoterraform.model;

import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete POJO representing an AWS Elastic IP (aws_eip).
 * Inherits fields like uniqueId, resourceType, arguments, and terraformOutputs from CloudResource.
 */
@NoArgsConstructor // Needed for Jackson deserialization
@ToString(callSuper = true) // Include fields from CloudResource in toString()
public class EipResource extends CloudResource {
    @Override
    public String getResourceTypeString() {
        return "";
    }
    // Arguments might include 'instance_ref', 'network_interface_ref', 'vpc', 'tags'.
}

