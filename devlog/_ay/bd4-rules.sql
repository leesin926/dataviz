SET NAMES utf8mb4;
SELECT CONCAT('@@RULE ', id, ' | ', name, ' | ds=', IFNULL(dataset_id,'NULL'), ' | ', scope_type, '=', scope_value, ' | cond=', IFNULL(conditions_json,'-'), ' | cols=', IFNULL(restricted_columns,'-'), ' | en=', enabled, ' | ten=', IFNULL(tenant_id,'NULL'), ' | del=', deleted) r FROM db_analysis.data_perm_rule ORDER BY id;
