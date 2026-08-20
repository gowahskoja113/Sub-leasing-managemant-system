UPDATE properties
SET managed_by = operation_manager_id
WHERE operation_manager_id IS NOT NULL
  AND (managed_by IS NULL OR managed_by <> operation_manager_id);

UPDATE properties
SET managed_by = NULL
WHERE operation_manager_id IS NULL
  AND managed_by IS NOT NULL;
