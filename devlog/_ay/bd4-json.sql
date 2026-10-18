SET NAMES utf8mb4;
USE db_model;
SELECT CONCAT('@@J ', id, ' DIMS ', COALESCE(dimensions,'(null)')) FROM model_dataset WHERE id IN (901,902,903);
SELECT CONCAT('@@J ', id, ' METS ', COALESCE(metrics,'(null)')) FROM model_dataset WHERE id IN (901,902,903);
SELECT CONCAT('@@J ', id, ' FILT ', COALESCE(filters,'(null)')) FROM model_dataset WHERE id IN (901,902,903);
SELECT CONCAT('@@J ', id, ' SQL ', COALESCE(sql_query,'(null)')) FROM model_dataset WHERE id = 902;
USE db_analysis;
SELECT CONCAT('@@R ', id, ' CFG ', COALESCE(config,'(null)')) FROM analysis_report WHERE id IN (901,902,903);
