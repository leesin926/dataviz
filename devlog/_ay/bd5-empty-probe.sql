SET NAMES utf8mb4;
SELECT CONCAT('@@QH ~|~ ', IFNULL(id,'-'), ' ~|~ u', IFNULL(user_id,'-'), ' ~|~ ds', IFNULL(dataset_id,'-'),
  ' ~|~ rc', IFNULL(row_count,'-'), ' ~|~ st', IFNULL(status,'-'), ' ~|~ ', IFNULL(create_time,'-'),
  ' ~|~ SQL[', IFNULL(LEFT(REPLACE(REPLACE(IFNULL(sql_text,''),'\n',' '),'\r',''),600),'-'), ']')
  FROM db_analysis.query_history WHERE id >= 906 ORDER BY id;
