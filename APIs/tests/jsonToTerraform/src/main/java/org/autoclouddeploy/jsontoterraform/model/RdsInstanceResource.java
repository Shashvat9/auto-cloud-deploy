package org.autoclouddeploy.jsontoterraform.model;

import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Concrete POJO representing an AWS RDS Database Instance (aws_db_instance).
 * Inherits fields like uniqueId, resourceType, arguments, and terraformOutputs from CloudResource.
 */
@NoArgsConstructor // Needed for Jackson deserialization
@ToString(callSuper = true) // Include fields from CloudResource in toString()
public class RdsInstanceResource extends CloudResource {
    @Override
    public String getResourceTypeString() {
        return "";
    }
    // Arguments might include 'identifier', 'engine', 'engine_version', 'instance_class',
    // 'allocated_storage', 'db_subnet_group_name_ref', 'vpc_security_group_ids_ref',
    // 'username', 'password', 'skip_final_snapshot', 'tags'.
}
