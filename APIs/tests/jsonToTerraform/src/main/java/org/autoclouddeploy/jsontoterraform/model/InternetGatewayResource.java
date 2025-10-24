package org.autoclouddeploy.jsontoterraform.model;

import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete POJO representing an AWS Internet Gateway (aws_internet_gateway).
 * Inherits fields like uniqueId, resourceType, arguments, and terraformOutputs from CloudResource.
 */
@NoArgsConstructor // Needed for Jackson deserialization
@ToString(callSuper = true) // Include fields from CloudResource in toString()
public class InternetGatewayResource extends CloudResource {
    @Override
    public String getResourceTypeString() {
        return "";
    }
    // Arguments might include 'vpc_id_ref', 'tags'.
}
