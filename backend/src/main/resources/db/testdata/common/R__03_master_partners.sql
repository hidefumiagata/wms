-- ============================================================
-- Repeatable migration: 取引先マスタ（共通マスタ）
-- 仕入先10社 + 出荷先10社 = 20社
-- ============================================================

INSERT INTO partners (partner_code, partner_name, partner_name_kana, partner_type, address, phone, contact_person, email, is_active, created_by, updated_by, created_at, updated_at)
SELECT v.partner_code, v.partner_name, v.partner_name_kana, v.partner_type, v.address, v.phone, v.contact_person, v.email, true, 1, 1, now(), now()
FROM (VALUES
    -- 仕入先 (SUPPLIER)
    ('SUP001', '東京食品株式会社',       'トウキョウショクヒン',     'SUPPLIER', '東京都中央区日本橋1-1-1',  '03-1234-0001', '田中 一郎', 'tanaka@tokyo-foods.example.com'),
    ('SUP002', '大阪物産株式会社',       'オオサカブッサン',         'SUPPLIER', '大阪府大阪市北区梅田2-2-2', '06-1234-0002', '山田 太郎', 'yamada@osaka-bussan.example.com'),
    ('SUP003', '北海道農産有限会社',     'ホッカイドウノウサン',     'SUPPLIER', '北海道札幌市中央区南1条1',  '011-123-0003', '佐藤 二郎', 'sato@hokkaido-nousan.example.com'),
    ('SUP004', '九州水産株式会社',       'キュウシュウスイサン',     'SUPPLIER', '福岡県福岡市博多区1-3-3',  '092-123-0004', '鈴木 花子', 'suzuki@kyushu-suisan.example.com'),
    ('SUP005', '信州精密工業株式会社',   'シンシュウセイミツコウギョウ', 'SUPPLIER', '長野県松本市中央3-3-3', '0263-12-0005', '高橋 次郎', 'takahashi@shinshu-seimitsu.example.com'),
    ('SUP006', '中部資材株式会社',       'チュウブシザイ',           'SUPPLIER', '愛知県名古屋市中区栄4-4-4', '052-123-0006', '伊藤 三郎', 'ito@chubu-shizai.example.com'),
    ('SUP007', '東北包装株式会社',       'トウホクホウソウ',         'SUPPLIER', '宮城県仙台市青葉区本町5-5', '022-123-0007', '渡辺 四郎', 'watanabe@tohoku-housou.example.com'),
    ('SUP008', '関西化学工業株式会社',   'カンサイカガクコウギョウ', 'SUPPLIER', '兵庫県神戸市中央区三宮6-6', '078-123-0008', '小林 五郎', 'kobayashi@kansai-kagaku.example.com'),
    ('SUP009', '四国青果株式会社',       'シコクセイカ',             'SUPPLIER', '愛媛県松山市大街道7-7',     '089-123-0009', '加藤 六郎', 'kato@shikoku-seika.example.com'),
    ('SUP010', '沖縄物流株式会社',       'オキナワブツリュウ',       'SUPPLIER', '沖縄県那覇市牧志8-8',       '098-123-0010', '吉田 七郎', 'yoshida@okinawa-butsuryu.example.com'),
    -- 出荷先 (CUSTOMER)
    ('CUS001', 'マルチストア渋谷店',     'マルチストアシブヤテン',   'CUSTOMER', '東京都渋谷区神南1-1-1',     '03-2345-0001', '中村 美咲', 'nakamura@multistore.example.com'),
    ('CUS002', 'フレッシュマート新宿店', 'フレッシュマートシンジュクテン', 'CUSTOMER', '東京都新宿区西新宿2-2-2', '03-2345-0002', '松本 健太', 'matsumoto@freshmart.example.com'),
    ('CUS003', 'デイリーショップ池袋店', 'デイリーショップイケブクロテン', 'CUSTOMER', '東京都豊島区東池袋3-3-3', '03-2345-0003', '井上 美優', 'inoue@dailyshop.example.com'),
    ('CUS004', 'スーパー横浜港北店',     'スーパーヨコハマコウホクテン',   'CUSTOMER', '神奈川県横浜市港北区4-4-4', '045-234-0004', '木村 大輔', 'kimura@super-yokohama.example.com'),
    ('CUS005', 'グローバル商事株式会社', 'グローバルショウジ',       'CUSTOMER', '東京都千代田区丸の内5-5-5', '03-2345-0005', '林 真由美',  'hayashi@global-shouji.example.com'),
    ('CUS006', 'コンビニサプライ株式会社', 'コンビニサプライ',       'CUSTOMER', '埼玉県さいたま市大宮区6-6', '048-234-0006', '清水 翔太', 'shimizu@conveni-supply.example.com'),
    ('CUS007', 'ネットスーパーαα',      'ネットスーパーアルファ',   'CUSTOMER', '千葉県千葉市中央区7-7',     '043-234-0007', '山本 優花', 'yamamoto@net-super-alpha.example.com'),
    ('CUS008', '業務用食品卸 ベスト',    'ギョウムヨウショクヒンオロシベスト', 'CUSTOMER', '東京都大田区蒲田8-8-8', '03-2345-0008', '森 拓也', 'mori@best-foods.example.com'),
    ('CUS009', 'ホテルサプライ東京',     'ホテルサプライトウキョウ', 'CUSTOMER', '東京都港区赤坂9-9-9',       '03-2345-0009', '阿部 さくら', 'abe@hotel-supply.example.com'),
    ('CUS010', 'レストランチェーン味楽', 'レストランチェーンミラク', 'CUSTOMER', '東京都目黒区自由が丘10-10', '03-2345-0010', '石井 涼太', 'ishii@miraku-chain.example.com')
) AS v(partner_code, partner_name, partner_name_kana, partner_type, address, phone, contact_person, email)
WHERE NOT EXISTS (SELECT 1 FROM partners p WHERE p.partner_code = v.partner_code);
