package org.autoclouddeploy.jsontoterraform.model;

import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete POJO representing an AWS Auto Scaling Group (aws_autoscaling_group).
 * Inherits fields like uniqueId, resourceType, arguments, and terraformOutputs from CloudResource.
 */
@NoArgsConstructor // Needed for Jackson deserialization
@ToString(callSuper = true) // Include fields from CloudResource in toString()
public class AutoScalingGroupResource extends CloudResource {
    @Override
    public String getResourceTypeString() {
        return "";
    }
    // Arguments might include 'name', 'launch_configuration_ref' or 'launch_template_ref',
    // 'min_size', 'max_size', 'desired_capacity', 'vpc_zone_identifier_ref' (list of subnet refs),
    // 'target_group_arns_ref' (list of LB target group refs), 'health_check_type', 'tags'.
}
