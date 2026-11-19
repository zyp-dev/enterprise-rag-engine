-- 旅游攻略知识库（12 篇）：旅行社客服知识库演示语料
-- 使用：mysql -uroot -p123456 --default-character-set=utf8mb4 < travel-docs.sql
SET NAMES utf8mb4;
TRUNCATE TABLE doc_db.kb_document;
TRUNCATE TABLE doc_db.kb_document_version;
TRUNCATE TABLE search_db.doc_chunk;

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '西藏高原旅行攻略', '进藏需准备身份证和边防证：前往珠峰大本营、阿里、墨脱等边境地区必须提前在户籍地或拉萨办理边防证，建议预留1个工作日。高原反应通常在抵达后6-12小时出现，表现为头痛、失眠、恶心，初到拉萨前48小时不要洗澡、不要剧烈运动，可提前一周服用红景天。最佳旅行季节为5-10月，其中7-8月为雨季，珠峰可见率较低。拉萨市区海拔3650米，布达拉宫需提前一天预约门票，旺季门票200元。从拉萨到纳木错车程约4小时，纳木错湖面海拔4718米，湖边住宿条件简陋，建议自备氧气瓶。林芝地区海拔较低，适合作为进藏第一站适应高原。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '东南亚签证指南', '泰国落地签：需准备护照（有效期6个月以上）、2寸白底照片2张、往返机票行程单、酒店预订单，签证费2000泰铢（约合400元人民币），停留期15天。建议携带等值10000泰铢现金备查。越南签证需提前办理电子签或另纸签，电子签费用25美元，出签约3个工作日，停留期30天。柬埔寨可办理落地签（30美元）或电子签（36美元），金边、暹粒机场均可办理落地签。马来西亚对中国游客实行免签政策，停留期30天，需出示往返机票和酒店订单。新加坡需提前申请电子签，通过旅行社代办约3-5个工作日出签。通用准备：所有国家均要求护照有效期大于6个月，建议复印护照首页和签证页各两份分开存放。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '冬季东北装备清单', '哈尔滨冬季气温可达零下25度，保暖核心是三层穿衣法：内层速干保暖内衣（避免纯棉），中层抓绒或轻薄羽绒，外层防风防水的冲锋衣或厚羽绒服。下身建议加绒冲锋裤配保暖内裤，鞋选高帮雪地靴并加大一码以便穿厚羊毛袜。必备配件：能触屏的加绒手套、护耳帽或雷锋帽、围巾、暖宝宝（贴脚底和手机背面）。电子设备注意：手机在低温下会快速掉电甚至关机，贴身存放，充电宝也需保暖。室内外温差大，进屋前拍掉身上积雪防止融化浸湿衣服。冰面行走建议购买冰爪套在鞋上，防滑效果显著。滑雪场租赁装备齐全，无需自带雪板。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '川西自驾路线', '川西小环线全程约1100公里，建议4-5天：成都—四姑娘山—丹巴—新都桥—康定—成都。新都桥海拔3300米，是摄影天堂，秋季（10月中下旬）杨树金黄最出片。大环线约2400公里，需8-10天，经稻城亚丁、理塘、色达，色达五明佛学院海拔4000米，注意高原反应。路况提示：折多山、高尔寺山垭口冬季易积雪暗冰，5-10月通行条件最好；G318部分路段塌方多发，出发前查交通管制公告。车辆建议SUV及以上底盘，全程加油站间隔最远约150公里，见加油站就补满。门票：四姑娘山双桥沟150元，稻城亚丁门票加观光车266元。沿途住宿旺季（7-8月、国庆）需提前3天以上预订。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '潜水考证指南', 'OW（开放水域潜水员）证书是最基础的潜水证，由PADI等机构颁发，课程通常4天：1天理论学习加1天平静水域技巧加2天开放水域4潜，费用约3000-4000元。考证条件：年满10岁，会游泳（200米游泳或300米浮潜）。AOW进阶证需2天5潜，可下潜至30米（OW限18米）。东南亚考证性价比高：菲律宾薄荷岛、泰国涛岛是全球考证最便宜的地区之一，涛岛OW约2000-2500元含住宿。考证前24小时不能饮酒，单次潜水后18小时内不能坐飞机。耳压平衡是新手最大难关，下潜时每米做一次捏鼻鼓气。近视者可戴隐形眼镜或租用带度数的面镜。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '穷游住宿省钱技巧', '青旅床位价格通常在30-80元每晚，适合单人背包客，注意选择评分4.5分以上、有独立储物柜和公共区域的分店；入住自备拖鞋、耳塞和眼罩。民宿整租适合2-4人结伴，人均摊薄后常比快捷酒店便宜，平台比价时用连住优惠和新人券叠加。淡旺季差价巨大：丽江、大理等热门目的地淡季（11月-次年3月）房价可低至旺季三分之一。换宿（打工换宿）适合长线旅行者，每天工作4-6小时换免费住宿，注意提前确认工作内容和评价。长途夜班火车或大巴可省一晚房费。预订技巧：先用比价平台比价，再到酒店官方渠道查看会员价，酒店前台直订有时比平台更便宜。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '长途火车旅行攻略', '春运和节假日火车票开售即秒光，技巧：提前在12306添加乘车人信息，开售时间（通常10:00或14:00）准点提交；抢不到全程票可买多买一站或少买一站然后上车补票；候补购票成功率较高，建议候补加抢票软件双管齐下。硬座过夜三件套：U型枕、眼罩、耳塞。硬卧选下铺方便但易被踩，上铺安静但空间矮，中铺折中。长途车自备：充电宝（2万毫安内）、保温杯、零食、拖鞋。车站取票进站高峰是开车前40分钟，预留足够时间。学生票每年4次，硬座5折、硬卧75折，需学生证和优惠卡。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '海岛游行李清单', '东南亚海岛（普吉、长滩、巴厘岛）基础清单：防晒霜SPF50+（珊瑚友好型）、防水袋、速干衣裤3套、人字拖、太阳镜、遮阳帽。浮潜装备：面镜和呼吸管建议自备（租赁卫生堪忧），脚蹼可租；水母衣既能防晒又能防海蜇。药品：防蚊液（登革热高发区必备）、肠胃药、晕船药、创可贴。电子设备：手机防水袋加浮力手环，无人机需提前查当地法规。货币：泰国、印尼、菲律宾均以现金为主，提前换汇或当地ATM取现（认准银联标志）。浮潜安全：远离螺旋桨、结伴下水、穿救生衣，雨季（6-10月）注意风浪预警。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '高原徒步入门（雨崩稻城）', '雨崩徒步是入门级高原徒步：从西当村进入约12公里爬升800米，普通游客6-8小时可完成，也可乘越野车进村（200元每人）。徒步装备：中帮防水徒步鞋、登山杖两支、30L背包、头灯、雨衣。高反预防：徒步海拔3000-3900米，出发前在飞来寺（3400米）适应一晚，携带葡萄糖口服液和布洛芬。稻城亚丁长线（牛奶海、五色海）往返10公里爬升500米，海拔4500米以上，体力一般者建议骑马至舍身崖（300元）再徒步。徒步纪律：不走夜路、不脱离主路、垃圾带下山。最佳季节10月中旬，秋色加少雨。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '旅行保险选购指南', '旅行险四大核心保障：医疗费用（东南亚建议保额20万起）、紧急救援（含直升机转运，高原地区必选）、行程变更（航班延误、取消）、财产损失（行李、证件）。购买时机：出发前1-2天购买生效即可，但含航班延误责任的产品通常要求提前24小时以上投保。高原徒步、潜水等高风险运动需选择承保高风险运动的产品，普通旅行险不赔。理赔要点：就医保留所有单据和诊断证明，航班延误向航司索取延误证明，证件丢失先报警拿报案回执。保费参考：东南亚7天约30-80元，含高风险运动约100-200元。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '自驾露营装备清单', '基础露营三件套：帐篷（三季帐适合春夏秋）、睡袋（按目的地夜间最低温选温标，比环境温度低5度更保险）、防潮垫（蛋巢垫性价比高）。炊具：卡式炉加气罐（高原需高山气罐）、套锅、折叠水桶。照明：头灯每人一个、营地灯。桌椅：折叠桌加月亮椅提升幸福感。电源：大容量移动电源（2万毫安）加车充逆变器。安全：急救包、防熊喷雾（西部山区）、车载灭火器。营地选择：远离河道（防山洪）、背风、平整；高原营地注意防风绳打满。入营时间控制在日落前2小时，摸黑扎营是大忌。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));

INSERT INTO doc_db.kb_document (tenant_id, title, content) VALUES
(1, '第一次出国准备清单', '出国前6个月：检查护照有效期（需大于回国日期6个月）。3个月：确定目的地签证类型，拍签证照片（各国尺寸不同，白底2寸最通用）。1个月：购买机票（比价后官网直购）、预订酒店（打印英文确认单）、购买旅行险。1周：换汇（机场汇率差，提前银行预约）、开通国际漫游或购买当地电话卡（东南亚落地机场买最便宜）、下载离线地图和翻译软件。出发前：复印护照、签证、机票酒店单各两份，一份放家里一份随身；记下中国驻当地使领馆电话。海关须知：新鲜水果、肉类、种子禁止携带入境，药品带原包装和英文说明书。');
SET @did = LAST_INSERT_ID();
INSERT INTO doc_db.kb_document_version (document_id, version, content) VALUES (@did, 1, (SELECT content FROM doc_db.kb_document WHERE id=@did));
