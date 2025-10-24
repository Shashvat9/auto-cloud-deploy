package org.autoclouddeploy.jsontoterraform.model;

import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete POJO representing an AWS Application Load Balancer (aws_lb).
 * Inherits fields like uniqueId, resourceType, arguments, and terraformOutputs from CloudResource.
 * NOTE: We assume 'aws_lb' corresponds to ALB based on the diagram's likely intent.
 */
@NoArgsConstructor // Needed for Jackson deserialization
@ToString(callSuper = true) // Include fields from CloudResource in toString()
public class AlbResource extends CloudResource {
    @Override
    public String getResourceTypeString() {
        return "";
    }
    // Arguments might include 'name', 'internal', 'load_balancer_type' (should be "application"),
    // 'security_groups_ref', 'subnets_ref', 'tags'.
}
