SET NAMES utf8mb4;
-- 读回两块屏的 JSON（供 bd5-contract.cjs 与声明逐字比对）；分隔符 ~|~ 的理由见 bd4-gen-model.mjs
-- 跑法必须带 --raw：mysql -B 默认把 \\ 折成 \，JSON 里的转义引号被吃掉一层后
--   JSON.parse 直接炸，症状是"库里与声明不一致"——其实库里是好的，是探针自己改了字节。
--   本轮真实踩过（画布文案里一个英文引号就足够），所以判据写在跑法里而不是靠人记。
USE db_screen;
SELECT CONCAT('@@SCREEN ', id, ' ~|~ ', name, ' ~|~ ', status, ' ~|~ ', IFNULL(share_token,'NULL')) FROM screen WHERE id IN (901, 911) ORDER BY id;
SELECT CONCAT('@@CFG ', id, ' ~|~ ', config_json) FROM screen WHERE id IN (901, 911) ORDER BY id;
SELECT CONCAT('@@CMP ', id, ' ~|~ ', components_json) FROM screen WHERE id IN (901, 911) ORDER BY id;
SELECT CONCAT('@@VAR ', id, ' ~|~ ', IFNULL(variants_json,'NULL')) FROM screen WHERE id IN (901, 911) ORDER BY id;
