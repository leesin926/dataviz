SET NAMES utf8mb4;
SELECT '== A. db_datasource tables ==' AS s;
SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='db_datasource' ORDER BY TABLE_NAME;
SELECT '== B. datasource rows ==' AS s;
SELECT * FROM db_datasource.datasource ORDER BY id;
