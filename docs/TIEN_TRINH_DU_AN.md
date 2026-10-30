# TỔNG HỢP TIẾN TRÌNH DỰ ÁN "ĐUA XE SINH TỒN"

Tài liệu bàn giao để tiếp tục làm việc ở phiên chat mới mà không bị thiếu sót. Cập nhật đến phiên bản v7.2 (ngày 31/10/2026). TỪ v7.0 GAME ĐÃ LÀM LẠI THÀNH KIỂU THỦ TRẠI — xem mục v7.0 trước tiên; các mục v1–v6 là lịch sử.

## 1. Thông tin chung

| Mục | Giá trị |
|---|---|
| Tên game | Đua Xe Sinh Tồn (package com.khongvinh.carsurvival) |
| Thể loại | Lái xe sinh tồn 2D cuộn ngang, phong cách Earn to Die |
| Nền tảng | Android 7.0+ (minSdk 24), màn hình ngang |
| Ngôn ngữ / công nghệ | Kotlin + Canvas (SurfaceView), không engine, không thư viện ngoài, không file ảnh/âm thanh |
| Repo GitHub | khongvinh/2d-game-car |
| Nhánh làm việc | claude/2d-driving-game-f0kpjk (chưa có Pull Request, chưa merge) |
| Commit mới nhất | xem git log nhánh làm việc (v7.0: làm lại thành game thủ trại — ngày lục soát/lên đường, đêm thủ thành) |
| Phiên bản hiện tại | v7.2 (versionCode 20) — số phiên bản hiện góc dưới trái màn hình chính (Game.VERSION, nhớ tăng mỗi bản) |
| Build tự động | GitHub Actions "Build APK" (.github/workflows/android.yml) — đang xanh, xuất artifact DuaXeSinhTon-apk |
| Khóa ký | app/debug.keystore (cố định, mật khẩu android) nên các bản cài đè lên nhau được |

## 2. Lịch sử yêu cầu và các phiên bản

### v1.0 — Game đua xe nhìn từ trên xuống (đã thay thế)
- Yêu cầu gốc: game lái xe 2D vượt chướng ngại vật trong thời gian giới hạn; hết giờ thì vòng bo tiêu diệt; vật phẩm: máu, tiền, lá chắn, vũ khí; game Android.
- Làm: xe nhìn từ trên xuống, 4 làn đường, vòng bo hình tròn thu hẹp, cửa hàng nâng cấp.
- Gặp lỗi: chưa push được do GitHub chưa cấp quyền; sau đó người dùng cấp quyền và push được.
- Không có Android SDK trong container (dl.google.com bị chặn) nên dựng chuỗi build APK thủ công (xem mục 6).

### v2.0 — Mở rộng luật chơi (đã thay thế)
- Vòng bo 3 giai đoạn: Bo 1 = 60 giây, Bo 2 = 20 giây, Bo 3 = 5 giây đếm ngược.
- Hiệu ứng âm thanh tổng hợp bằng code.
- Độ khó cấp số cộng: màn 1 có 3 chướng ngại, mỗi màn sau +1.
- 20 ải, mỗi ải 5 màn.
- Qua ải 1 mở Gara (đổi xe, vũ khí: nỏ, máy ném đá); qua ải 5 mở vật phẩm Nhân bản (x2 mạng trong 10 giây).

### v3.0 — Làm lại theo phong cách Earn to Die (nền tảng hiện tại)
- Người dùng gửi ảnh game Earn to Die Rogue và yêu cầu làm giống.
- Chuyển sang màn hình ngang, cuộn ngang, xe có vật lý (nhún, bay, lật), địa hình đồi sa mạc, zombie, thùng gỗ, thùng phuy, xác xe.
- Vòng lặp chơi: mỗi lần chạy là một ngày, kiếm tiền, nâng cấp, chạy lại cho đến khi tới trạm cứu hộ.
- Giữ lại các yêu cầu v2: bão (vòng bo) 3 giai đoạn đuổi phía sau, 20 ải x 5 màn, gara, nhân bản, âm thanh.
- Sửa CI: bỏ android-actions/setup-android@v3 (lỗi "Failed to find package 'tools'"), tự cài platform 35 + build-tools 35 bằng sdkmanager có sẵn trên máy GitHub.

### v4.0 — Gồ ghề hơn, hiệu ứng, nhạc nền, đồ họa
- Địa hình gồ ghề hơn: đá nhô, ổ gà, đoạn ván giặt, dốc nhảy, đồi dốc hơn; mặt dốc lên tối đa khoảng 38 độ; thung lũng chữ V được bo tròn.
- Xe tự cân bằng nhẹ khi bay nếu người chơi không bấm nghiêng.
- Máu dưới 15%: xe bốc cháy, khói đen, cảnh báo "XE ĐANG CHÁY!".
- Xe bị phá hủy hoặc bị bão nuốt: nổ "BÙMMM!", mảnh vỡ văng, xác cháy đen khoảng 2 giây rồi hiện bảng tổng kết.
- Mỗi lần va chạm hiện số máu bị trừ (ví dụ "-10") và viền màn hình chớp đỏ.
- Nhạc nền tổng hợp bằng code: chill (menu, đầu ván), kịch tính (qua nửa đường, bão tăng tốc, bão gần, máu thấp), chiến thắng (tới trạm). Nút 🎵 bật/tắt.
- Bảng tổng kết chỉ hiện điểm, điểm cao nhất, 4 ô thống kê và mục 🏆 Thành tích; bỏ các gợi ý.
- Menu có khẩu hiệu "CHẠY CÀNG XA — CÀNG SỐNG SÓT!" trên dải băng đỏ; bỏ các dòng hướng dẫn.
- Trau chuốt đồ họa: bầu trời nhiều lớp, mặt trời tỏa sáng, mây, kền kền, thành phố đổ nát, núi có sương, mặt đất có cỏ khô/sỏi, bóng đổ, bánh xe nhún, đèn pha, zombie có viền, HUD dạng kính mờ, đồng hồ tốc độ.

### v4.1 — Thanh độ bền vật cản
- Khi xe tông vật cản (xác xe, thùng gỗ) mà không đủ tốc độ phá ngay: hiện thanh độ bền 4 nấc trên vật cản.
- Mỗi cú tông có đà (tốc độ vào vật cản trên 150 đơn vị/giây, khoảng 18 km/h) trừ 25%; cú thứ 4 thì "PHÁ TAN!".
- Có thời gian chờ 0,35 giây giữa hai cú để phải lùi lấy đà thật sự; mỗi cú xe mất ít máu (tổng 4 cú khoảng 20 máu).
- Đạn bắn vào vật cản cũng làm tụt thanh này. Đủ tốc độ thì vẫn phá ngay như cũ.
- Người dùng xác nhận "Ok rồi đó".

### v4.2 — Trùm cuối mỗi 5 ải
- Yêu cầu: thêm trùm cuối vào cuối mỗi 5 ải; vật phẩm gần chặng cuối nhiều để đủ sức bắn trùm; KHÔNG nhắc nhở/hướng dẫn, để người chơi tự khám phá.
- Trùm ở ải 5, 10, 15, 20 (bản v4.2 ban đầu là 4 zombie khổng lồ; v4.3 đã thay bằng 4 loài khác nhau, xem dưới).
- Trùm đứng trong đấu trường trước trạm (từ goalX-1150 đến goalX-260), đi chậm về phía xe, ném tảng đá (sát thương 14 + 3 x cấp), đập đất khi xe ở gần dưới 300 (sát thương 10 + 4 x cấp, hất văng xe).
- Cách hạ: vũ khí tự bắn vào trùm; tông có đà (sát thương = tốc độ vào / 35, x1,5 khi bật tên lửa, mỗi cú cách 0,45 giây, xe mất 5 + 2 x cấp máu); nổ (máy ném đá, thùng phuy) gây gấp đôi.
- Cổng trạm đóng (thanh chắn đỏ trắng) cho đến khi trùm gục; xe không qua được vạch goalX-140.
- Hạ trùm: nổ lớn, thưởng 300 x cấp $, điểm +3000 x cấp, thành tích "👹 Hạ <tên trùm>".
- Ải có trùm: không đặt zombie/vật cản trong 1300 đơn vị cuối; đấu trường phẳng dần (fade địa hình 2600 đơn vị cuối, không có đá/dốc nhảy trong 2300 đơn vị cuối); 28% quãng đường cuối có thêm 4 vật phẩm mỗi 100m (2 đạn, sửa xe hoặc xăng, lá chắn hoặc đạn), không thông báo.
- Thanh máu trùm hiện ở giữa trên màn hình khi trùm "thức dậy" (xe cách 1500 đơn vị); tiếng gầm (Sfx ROAR); nhạc chuyển sang kịch tính.
- Mô phỏng: ải 5 xe gốc + 2 cấp đạn hạ trùm trong khoảng 20 giây; ải 20 xe nâng cấp cấp 4 + nỏ hạ Vua Zombie và về đích còn 21 máu.

### v4.3 — Sửa âm thanh trễ, chọn bản đồ, 20 bản đồ, quái vật theo vùng, 4 trùm mới
- Yêu cầu: (1) âm thanh trong game đôi khi chậm/mất cho tới khi nhạc nền chạy một lúc; (2) giao diện chọn map, mỗi ải một map với background riêng; (3) mỗi ải quái khác nhau: ải 5 zombie, ải 10 rồng phun lửa, ải 15 dơi hút máu, ải 20 quái vật ngoài hành tinh.
- Sửa âm thanh (Sfx.kt, Music.kt): nguyên nhân là tạo hiệu ứng, soạn nhạc và nạp SoundPool cùng lúc khi khởi động. Nay tạo hiệu ứng trước (luồng ưu tiên cao), nhạc chờ hiệu ứng xong (CountDownLatch `sfx.ready`, tối đa 15 giây) mới soạn; cả hai lưu cache ra file (`sfx_3_<tên>.wav`, `music_v1_<bài>.pcm`) nên lần mở sau không phải tạo lại; chỉ phát mẫu đã nạp xong (OnLoadCompleteListener); 16 luồng phát; ưu tiên (nổ/trúng/gầm/thắng/thua = 2, bắn/tiếp đất = 0); giãn cách tối thiểu mỗi âm (bắn 90ms, văng/xu/gỗ/tiếp đất 45ms, còn lại 25ms) để không nghẽn.
- Bản đồ (Themes.kt mới): 4 vùng x 5 map, mỗi map tên + bảng màu trời/núi/đất riêng.
  - Sa mạc (ải 1–5): Sa Mạc Bình Minh, Hẻm Núi Đỏ, Thị Trấn Hoang, Đồi Cát Hoàng Hôn, Nghĩa Địa Sa Mạc (bình minh → trưa → chiều → hoàng hôn → chạng vạng; chim kền kền, thành phố đổ nát).
  - Núi lửa (6–10): Chân Núi Lửa, Sông Dung Nham, Hẻm Tro Tàn, Miệng Núi Lửa, Hang Rồng (núi lửa phun dung nham, tàn lửa bay, cây cháy, đá nứt lửa).
  - Rừng đêm (11–15): Rừng Sương Mù, Đầm Lầy Ma, Làng Bỏ Hoang, Nghĩa Trang Trăng Máu, Lâu Đài Ma Cà Rồng (trăng, lâu đài + rừng thông, đom đóm, bí ngô, bia mộ, đèn đường; trăng đỏ ở 2 map cuối).
  - Hành tinh lạ (16–20): Vùng Thiên Thạch, Rừng Pha Lê, Căn Cứ Thất Thủ, Thung Lũng Tím, Tàu Mẹ Ngoài Hành Tinh (hành tinh có vành, tháp nhọn, pha lê phát sáng, nấm, đĩa bay, bào tử).
- Màn hình chọn bản đồ (State.MAPS): nút "🗺 ẢI n: TÊN MAP" ở menu (thay nút ◀ ▶), mở lưới 5 x 4 thẻ có cảnh thu nhỏ; 👹 = ải trùm, ✔ = đã vượt, 🔒 = chưa mở; chạm thẻ đã mở để chọn và về menu. Dòng phụ menu: vùng • độ dài • kỷ lục.
- Quái theo vùng (Entities.kt ZombieType): Sa mạc = zombie (thường, chạy nhanh, béo từ ải 2); Núi lửa = zombie cháy + THẰN LẰN LỬA (35%); Rừng đêm = zombie ma + DƠI (38%, bay lượn ở độ cao ~150 rồi lao xuống xe trong 320, chạm là nổ, trừ 4 máu x (1 - giáp)); Hành tinh lạ = NGƯỜI NGOÀI HÀNH TINH + KHỐI NHỚT (20%, to, chậm, nặng).
- 4 trùm mới (máu 50 / 100 / 140 / 170):
  - Ải 5 GÃ KHỔNG LỒ (zombie): ném tảng đá, đập đất.
  - Ải 10 RỒNG LỬA: bay, phun cầu lửa từ miệng; cứ 6 giây phun lửa 1,6 giây khi xe trong tầm 750 (14 máu/giây x (1 - giáp) trong 550); cứ 7 giây hạ cánh 2,4 giây (lúc đó tông được).
  - Ải 15 DƠI HÚT MÁU: bay, cứ 4,5 giây bổ nhào cắn (10 + 3 x cấp, hồi cho trùm 6 máu, cách nhau 0,8 giây); cứ 8 giây gọi 2 dơi con.
  - Ải 20 QUÁI VẬT NGOÀI HÀNH TINH: bắn quạt 3 cầu plasma (không rơi, tốc độ 620), đập đất.
  - Trùm bay chỉ bị tông khi hạ thấp; súng tự nhắm trùm theo mọi hướng (kể cả khi trùm ở sau/ trên xe); đạn trúng trùm bay trong phạm vi h/2 + 40.
- Mô phỏng (tools/dev/preview `gradle boss`, máu thử 400): ải 5 thắng 17,6 giây (còn 372); ải 10 thắng 15,2 giây (334); ải 15 thắng 29,6 giây (292); ải 20 thắng 40,8 giây (114) và tới VICTORY.
- Công cụ preview: thêm task `themes` (ảnh menu, màn chọn map, các ải 1/4/7/9/12/14/17/20) và `boss` (đánh 4 trùm).

### v4.4 — Gara lớn: 25 xe, 10 vũ khí có cấp, cày tiền, xe chạy mượt
- Yêu cầu: xe ban đầu là xe máy; qua ải 5 mở xe hơi, ải 10 bán tải, ải 20 xe tăng + bọc thép; giá xe +30%; thêm súng, dao, tên lửa… nhiều thuộc tính; phải cày (farm quái mới có tiền), mỗi loại xe 5 phiên bản; đồ họa xe đẹp hầm hố; xe chạy mượt, hết cứng nhắc. Người dùng xác nhận nhạc v4.3 đã khớp.
- Diễn giải: "qua ải 20 xe tăng, xe bọc thép" → Bọc thép mở ở ải 15 (lấp khoảng trống 5-10-15-20), Xe tăng mở khi qua ải 20 (thưởng sau khi phá đảo, dùng để cày lại). Có thể đổi dễ trong Garage.kt (VehicleClass.unlockStage).
- Code: Garage.kt mới (VehicleClass, Vehicle, Vehicles, WeaponType + stats theo cấp, PRICE_MUL = 1.3); CarArt.kt mới (vẽ 25 xe: xe máy có người lái + 5 kiểu, xe hơi rỉ sét/cơ bắp/rally/gai/tử thần, bán tải, bọc thép chở tiền/SWAT/lội nước/xe ủi/pháo đài, xe tăng có xích chạy, tháp pháo, rằn ri; 10 vũ khí; dao chém gắn mũi xe); bỏ CarType.
- Gara 2 tab: XE (5 dòng bên trái, ô xem lớn + 7 thanh chỉ số có vạch trắng so với xe đang dùng, xanh = tốt hơn, cam = kém hơn; 5 phiên bản bên dưới; nút MUA/CHỌN XE) và VŨ KHÍ (lưới 10 vũ khí; bảng chi tiết, 5 nấc cấp, chỉ số hiện tại → cấp sau; nút MUA/TRANG BỊ và NÂNG CẤP).
- Kinh tế cày: tiền quãng đường 0,4 → 0,25 $/m; thưởng vượt ải 200 → 120 x ải; tiền quái/vật cản x (1 + 0,12 x (ải - 1)) nên ải cao farm lời hơn; giá nâng cấp xe x1,5 + 100 x cấp²; máu trùm 50/120/180/240.
- Lưu dữ liệu: khóa mới ownedVeh, veh, ownedWpn, wpn, wl0..wl9, garageV = 2. Bản cũ được hoàn tiền xe đã mua (1500/3000/6000), Súng máy/Nỏ/Máy ném đá cũ giữ nguyên.
- Mượt hơn: ga/phanh và nghiêng tăng dần (ga lên ~0,12 giây, nhả ~0,07 giây, nghiêng ~0,1 giây) thay vì bật/tắt; nhún mềm hơn (SPRING 120 → 105, DAMP 10 → 9,5); bánh xe vẽ theo đúng độ nén nhún (tối đa 16); thân xe chúi/ngóc theo ga/phanh (squat, chỉ để vẽ).
- Vũ khí mới trong trận: đạn có tầm thật (hết tầm thì biến mất), shotgun chùm 5 viên, phun lửa đốt cháy, tên lửa tự dẫn + khói, laser tia tức thì, dao chém trước mũi; HUD đạn hiện ∞ với dao.
- Bảng vượt ải hiện chip mở khóa dòng xe/vũ khí mới.
- Mô phỏng (gradle boss, máu thử 400): Cào Cào + Súng lục cấp 2 đánh trùm ải 5 còn thiếu 3 máu sau 83 giây (phải nâng cấp); Mô Tô Chiến Binh + Súng máy cấp 2 thắng 12,8 giây; Siêu Xe Gai + Nỏ cấp 3 thắng rồng 15 giây; Bán Tải Lưới Thép + Phun lửa cấp 4 thắng dơi 20 giây; Bọc Thép Lội Nước + Tên lửa cấp 5 thắng ải 20 sau 16 giây. Vật lý: không lật ở mọi ải. Xe máy qua ải 1 trong 40 giây, nhận 658$.
- Công cụ: task preview `garage` (ảnh g_lineup 25 xe, màn gara ở 1600 và 1280 px, ảnh từng vũ khí trong trận).

### v4.5 — Hết kẹt xe, sửa núi xuyên đường, 20 kiểu đường, gara 3 tab, bảng xếp hạng, giao diện kim loại gỉ
- Yêu cầu (kèm 3 ảnh chụp: xe kẹt giữa thùng gỗ, núi lửa nhìn xuyên qua đất, ảnh mẫu đồ họa kiểu thép gỉ): sửa kẹt xe vào chướng ngại vật; sửa hình chồng (núi thấy xuyên qua đường); mỗi ải một kiểu đường (hầm chui, hang động, đồng bằng…); bỏ NÂNG CẤP ở màn hình chính vì đã có gara; thêm bảng xếp hạng quãng đường xa nhất ở màn hình chính; đồ họa giống ảnh 3. Câu hỏi CH Play/AAB: đã trả lời (xem mục 9), người dùng bảo để sau.
- Lỗi núi xuyên đường: Paint giữ alpha của đốm tàn lửa/đom đóm/bào tử (vẽ cuối nền) nên đất (vẽ bằng shader) bị trong suốt ở vùng Núi lửa/Rừng đêm/Hành tinh lạ. Sửa: đặt fill.alpha = 255 trước mọi lần vẽ bằng shader.
- Kẹt xe: (1) không đặt vật cản ở chỗ dốc > 0,45, đáy hố (thấp hơn hai bên 40 trong ±180) hay trên/ngay trước cầu (đặt quái thay vào); (2) giữ ga mà xe không tiến quá 40 đơn vị trong 2,2 giây và có thùng/xác xe trong tầm thì tự "PHÁ TAN!" và đẩy nhẹ (không áp dụng ở cổng trùm); (3) khi xe ép vào vật cản thì giảm xoay 40% để không bị chổng đuôi lật.
- Kiểu đường (Themes.ROADS, Terrain): RoadStyle(tên, mặt đường 0 đất / 1 nhựa / 2 kim loại / 3 đá, độ cao đồi, độ gồ ghề, số hầm, hang, số cầu, đáy vực 0 hẻm / 1 dung nham / 2 đầm / 3 hư không).
  - Ải 1 Đồng bằng; 2 Cầu vượt vực (3 cầu); 3 Xa lộ & hầm chui (nhựa, 2 hầm); 4 Đồi cát; 5 Đường mòn & hầm; 6 Đèo đá; 7 Cầu qua dung nham (4 cầu); 8 Hầm xuyên núi (3 hầm); 9 Hang dung nham; 10 Hang rồng; 11 Đồng bằng sương; 12 Cầu gỗ qua đầm; 13 Đường làng & hầm; 14 Đồi nghĩa trang; 15 Hầm ngục lâu đài; 16 Hố thiên thạch; 17 Hang pha lê; 18 Xa lộ căn cứ (kim loại, 3 hầm); 19 Cầu năng lượng; 20 Hành lang tàu mẹ.
  - Hầm/hang có trần thật (Terrain.ceilingAt, CLEARANCE 300): xe, đạn, tia laser, quái bay đều bị trần chặn. Hang chạy từ 18% đến 85% quãng đường; ải trùm luôn để trống 2900 đơn vị cuối. Trong hầm đường bằng hơn, không có dốc nhảy; vào hầm màn hình tối đi.
  - Vẽ: núi đá bao hầm, lòng hầm tối có sườn bê tông/thép, đèn trần chiếu hình nón, cửa hầm có sọc vàng đen; hang có nhũ đá, pha lê phát sáng, dung nham nhỏ giọt; cầu ván gỗ/cầu năng lượng có cột, dây võng, đáy vực dung nham sủi bọt/nước đầm/hư không đầy sao; mặt đường nhựa có vạch vàng + lan can, kim loại có đường sáng, đá có sỏi.
- Màn hình chính: bỏ nút NÂNG CẤP; nút CHẠY! và GARA; dòng phụ hiện vùng • kiểu đường • độ dài • kỷ lục; bảng "🏆 BẢNG XẾP HẠNG — CHẠY XA NHẤT" (top 5 trong 10 lượt lưu: mét, ải, xe, ngày; lưu prefs "runs"). Vào top 3 có thành tích "🏅 Hạng n bảng xếp hạng". Bảng xếp hạng chỉ trên máy (online cần Google Play Games, để sau).
- Gara mở ngay từ đầu, 3 tab: XE, VŨ KHÍ, NÂNG CẤP (6 nâng cấp cũ chuyển vào đây). Bỏ State.SHOP; nút ở bảng tổng kết/vượt ải đổi thành GARA.
- Đồ họa kiểu thép gỉ (giống ảnh mẫu): mọi bảng HUD/thẻ gara là tấm thép có vết gỉ, vết xước, đinh tán 4 góc, lòng tối; nút điều khiển là tấm thép có mặt màu, nút GA có sọc lửa; đồng hồ tốc độ viền thép bắt bu lông; nút menu có đinh tán; núi lửa có dòng dung nham phát sáng; tro bụi và mảnh vụn bay ngang màn (núi lửa), vệt bụi cát (sa mạc); thân xe có ánh kim loại và vết bẩn; tiếp đất mạnh tóe tia lửa; hạ quái văng đồng xu.
- Kiểm tra: bot chỉ giữ ga qua đủ 20 ải (task preview `drive`), không còn kẹt; vật lý không lật; 4 trùm thắng; preview `roads` chụp từng kiểu đường.

### v4.6 — Bỏ hẳn nâng cấp (xe tự mang chỉ số), tiếng động cơ
- Yêu cầu: bỏ hẳn chức năng nâng cấp vì gara đã đủ; thêm âm thanh tiếng động cơ xe.
- Bỏ enum Upgrade, tab NÂNG CẤP, SaveData.upgrades. Mỗi xe có mức "độ" sẵn tune = min(5, (thứ tự dòng x 5 + phiên bản) x 0,25) (Cub 0 → Mô Tô Chiến Binh 1 → Xe hơi 1,25..2,25 → Bán tải 2,5..3,5 → Bọc thép 3,75..4,75 → Xe tăng 5) thay cho cấp nâng cấp cũ: máu (100 + 30·tune)·hpMul, tốc độ tối đa (700 + 110·tune)·speedMul, lực kéo (520 + 80·tune)·powerMul, bám (1 + 0,15·tune)·grip, giáp min(70%, armor + 6%·tune), xăng (22 + 6·tune)·fuelMul giây, tên lửa 1,5 + 1,2·tune giây, đạn 20 + 20·tune. Gara hiện 9 chỉ số thật (Máu, Tốc độ km/h, Lực kéo, Giáp, Xăng giây ga, Bám đường, Tên lửa đẩy, Đạn, Sức húc).
- Lưu dữ liệu garageV = 3: hoàn tiền mọi cấp nâng cấp đã mua (công thức base x (cấp+1) x 1,5 + 100 x cấp², base 150/120/180/140/100/110) rồi xóa khóa up0..up5.
- Tiếng động cơ: mẫu lặp 0,4 giây tổng hợp (trục 50 Hz, 2 kỳ nổ mỗi vòng, tiếng rè ống xả, lặp liền không tiếng tách), phát lặp qua SoundPool (Sfx.engine), cao độ theo tốc độ (lên ga khi bay), to theo ga, +to khi bật tên lửa; mỗi dòng xe một giọng (xe máy cao 1,35, xe hơi 1,05, bán tải 0,92, bọc thép 0,8, xe tăng 0,68). Tắt ở menu/tạm dừng/xe nổ và khi ứng dụng xuống nền.

### v4.7 — Nâng cấp bằng ngọc đỏ (20 cấp), lô cốt bắn trả, xóa dữ liệu, âm thanh khi tạm dừng, cốt truyện
- Yêu cầu (kèm ảnh menu, màn tạm dừng, tab Nâng cấp cũ của v4.5): nút reset dữ liệu ở màn hình chính; bật/tắt âm thanh trong menu tạm dừng; nâng cấp nhiều mức, trả bằng ngọc đỏ nhặt trên đường (không dùng tiền), "nâng 1 vạch cần 20% ngọc" — hiểu là mỗi cấp đắt hơn cấp trước 20%; lô cốt bắn trả gây mất máu; xây dựng cốt truyện mạt thế zombie sinh tồn trên chiến xa.
- Ngọc đỏ (ItemType.GEM, vẽ viên ngọc giác cạnh đỏ): mỗi 100m có 1 + ải/6 + (0..1) viên, 40% treo cao 170–290 (phải nhảy); phá lô cốt +3, hạ trùm +10 x cấp. Ngọc nhặt trong lượt (runGems) cộng vào kho khi lượt kết thúc (cả khi chết). HUD hiện "+tiền$ +ngọc♦"; bảng tổng kết có ô ngọc đỏ; menu có chip ♦.
- Nâng cấp quay lại thành tab NÂNG CẤP trong gara (3 tab), 6 loại x 20 cấp, giá cấp n = round(5 x 1,2^n) ngọc (5, 6, 7, 9 … 160; tổng 934 ngọc/loại). Tác dụng mỗi cấp (cộng thêm vào chỉ số của xe): Động cơ +2,5% tốc độ +3% lực kéo; Bình xăng +5%; Tên lửa +0,15 giây; Giáp & máu +4% máu +1% giáp (tổng tối đa 75%); Đạn +6 viên; Lốp +2% bám. Lưu ở khóa ug0..ug5, gems. Thanh 20 nấc (xanh 1–10, vàng 11–15, đỏ 16–20).
- Lô cốt (ObstacleType.BUNKER 140x74): 1 + ải/3 cái mỗi ải, đặt chỗ bằng phẳng (không dốc/hố/cầu/hầm), cách ga cuối (ải trùm chừa 3300). Máu 8 + ải. Xoay súng theo xe trong khoảng -1150..+400, bắn loạt 3 viên rồi nạp max(0,9; 1,9 - 0,04 x ải) giây, đạn bay thẳng 900 + 15 x ải, sát thương (4 + 0,5 x ải) x (1 - giáp), lá chắn chặn được. Là vật cản cứng: tông có đà trừ 25% độ bền (xe nặng 50%), lá chắn không đè bẹp được; vũ khí tự ưu tiên bắn lô cốt. Bị phá: nổ lớn, +tiền 30 x hệ số ải, +3 ngọc. Có thanh máu khi bị thương.
- Màn hình chính: nút "↺ CHƠI LẠI" (hộp xác nhận "CHƠI LẠI TỪ ĐẦU?" — xóa tiền, ngọc, xe, vũ khí, nâng cấp, ải, bảng xếp hạng; giữ cài đặt âm thanh; sau đó chiếu lại lời mở đầu) và "📖 CỐT TRUYỆN" (đọc lại các trang đã mở). Tạm dừng: thêm nút "HIỆU ỨNG: BẬT/TẮT" và "NHẠC: BẬT/TẮT".
- Cốt truyện (Story.kt, State.STORY; chạm để sang trang, nút BỎ QUA): lời mở đầu 4 trang chiếu lần đầu mở game; chương 1–4 chiếu lần đầu vào mỗi vùng; kết thúc chiếu sau khi thắng ải 20 (bấm MENU). Lưu đã xem ở khóa story (bit 0 mở đầu, 1–4 chương, 5 kết).
- Kiểm tra: bot chạy 20 ải vẫn tới đích (thỉnh thoảng lật do bot không biết nghiêng); sát thương lô cốt mỗi ải khoảng 36–250 máu với xe 183 máu; ngọc mỗi lượt ~11 (ải 1) → ~40 (ải 10) → ~100 (ải 20); 4 trùm vẫn thắng với trang bị tương ứng.

## Cốt truyện — "Biên niên sử Chiến Xa"
- Mở đầu 1 — Ngày 0, cơn mưa đỏ: năm 2049 mưa thiên thạch đỏ bảy ngày đêm, bụi đỏ biến hàng tỷ người thành xác sống — MẠT THẾ.
- Mở đầu 2 — Người thợ máy cuối cùng: nhân vật là thợ máy ở thị trấn ven sa mạc, chỉ còn xe Cub, súng lục, vài can xăng; radio bắt được tín hiệu "Trạm Cứu Hộ… phía tây… còn người sống".
- Mở đầu 3 — Vòng bão tím: bão bụi tím (thứ biến người thành quái) đuổi phía sau; luật duy nhất: CHẠY CÀNG XA — CÀNG SỐNG SÓT!
- Mở đầu 4 — Ngọc đỏ và chiến xa: mảnh thiên thạch thành ngọc đỏ mang năng lượng, lắp vào động cơ để xe mạnh lên; dựng chiến xa từ xe máy tới xe tăng.
- Chương 1 — Sa mạc chết: lô cốt của bọn cướp đường vẫn tự bắn; Gã Khổng Lồ (cựu đô vật thị trấn) canh cổng trạm.
- Chương 2 — Vùng đất lửa: thiên thạch rơi vào miệng núi lửa, thằn lằn lửa tràn ra; Rồng Lửa canh Hang Rồng.
- Chương 3 — Rừng đêm vĩnh cửu: bụi tím che mặt trời, làng bỏ hoang, đàn dơi trong lâu đài cổ; chúa tể Dơi Hút Máu săn xe còn sáng đèn.
- Chương 4 — Hành tinh lạ: mưa đỏ là do tàu mẹ ngoài hành tinh gieo xuống để "dọn sạch" Trái Đất; hạ Quái Vật Ngoài Hành Tinh trong tàu mẹ.
- Kết — Bình minh mới: tàu mẹ nổ, bão tan, người sống sót đón chiến xa; radio lại rè — còn vùng đất chưa giải phóng, gara có Xe Tăng, quay lại đường đua.

### v4.8 — Gara kiểu xưởng (như Earn to Die 2), cây kỹ năng, mảnh xe, tài xế xuống xe lục soát & bắn quái, ít zombie
- Yêu cầu (kèm 3 ảnh mẫu: gara phòng gạch có nút tròn bên trái, xe "Locked — Find this car by looting buildings (0/5 parts found)", cây kỹ năng chuỗi nút +3/+12/+1 có nhánh lửa x2, thanh cấp ngôi sao + XP): làm các phần tương tự; thêm tài xế xuống xe đi lục đồ và bắn quái; zombie không quá nhiều.
- Ít zombie: số zombie/vật cản mỗi 100m = min(9, 2 + màn trong ải (0..4) + ải/5) (trước: 3 + màn toàn cục, tối đa 20); quái đi kèm 12% (trước 35%). Ải 4: bot mất 77 máu thay vì 715.
- Gara (Renderer.garageRoom/garageNav/garageTopBar): phòng tường gạch, ống nước, đèn treo chiếu sáng, cửa cuốn, sàn; 4 nút tròn bên trái (Nhà = menu, Xe, Vũ khí, Kỹ năng); thanh trên: ngôi sao cấp + thanh XP, ngọc ♦ và tiền $.
  - Tab Xe (showroom): 1 xe lớn giữa phòng, băng tên đen, trạng thái Đang dùng / Đã có / Khóa (xe mờ + ổ khóa), mũi tên ◀ ▶ duyệt 25 xe, 5 chip dòng xe, bảng 9 chỉ số bên phải, nút CHỌN XE / LẮP RÁP giá$ / THIẾU MẢNH x/5 / QUA ẢI n, nút CHẠY!.
  - Mở xe: cần đủ 5 mảnh (SaveData.parts, khóa pt0..pt24; PARTS_NEEDED = 5) rồi trả tiền lắp ráp (giá cũ). Xe Cub có sẵn; xe đã mua giữ nguyên. Mảnh luôn đi vào xe rẻ nhất chưa đủ mảnh của dòng đã mở (partTarget).
  - Tab Vũ khí: như cũ, dời sang phải cho vừa thanh nút.
  - Tab Kỹ năng (Skills.kt): 3 vòng x 7 nút chỉ số (Tên lửa +0,4s, Máu +12, Giáp +2%, Động cơ +4% tốc độ/lực kéo, Xăng +8%, Đạn +12, Lốp +4%; vòng 2 x1,25, vòng 3 x1,5) + 6 nút lửa (kỹ năng): Đạn lửa (+25% sát thương), Thu hoạch (+25% tiền), Nam châm (hút vật phẩm 300), Giáp gai (sức húc x1,5), Tự sửa chữa (+1,5 máu/giây), Lục soát nhanh (nhanh x2, +20% ra mảnh). Mua theo thứ tự; nút lửa mở khi nút cha đã mua. Giá nút thứ n (đếm số nút đã mua) = round(5 x 1,2^n) ngọc, tổng ~3.400 ngọc. Lưu khóa skills (bit). Ngọc đã tiêu cho 6x20 nâng cấp v4.7 được hoàn lại (garageV 4).
  - Cấp người chơi (Rank): XP mỗi lượt = mét/25 + số quái + 30 x ải nếu tới đích; cấp n cần 60 x n XP; mỗi lần lên cấp +10 ngọc (thành tích "⭐ Lên cấp").
- Nhà để lục soát (Building, 1 + ải/6 cái mỗi ải, chỗ bằng phẳng, xa lô cốt, không trong hầm/cầu/khu trùm): Nhà dân, Tạp hóa (x1,5 tiền), Trạm xăng (luôn có xăng, có mái bơm), Gara (85% ra mảnh xe), Nhà thuốc (luôn hồi máu). Cửa chưa lục có ánh đèn vàng, đã lục thì cửa bị đạp tung.
- Tài xế (Driver): dừng xe (< 60) gần nhà chưa lục → nút "XUỐNG XE". Đi bộ bằng ◀ ▶ (240/giây), súng ngắn tự ngắm bắn quái gần nhất trong 700 (1,5 sát thương, 0,32 giây/phát, không tốn đạn), máu 100. Ở cửa nhà: nút "LỤC SOÁT" (2,2 giây, đứng yên; bị cắn thì dừng), gần xe: "LÊN XE". Quái và lô cốt chuyển sang đuổi/bắn tài xế; bão cũng gây sát thương. Tài xế chết → "Tài xế bị quái vật hạ gục!". Chiến lợi phẩm: tiền (40 + 15 x ải) x 0,7..1,3; mảnh xe 50% (gara 85%, +20% kỹ năng); ngọc 3–6 (50%); xăng +35%, máu +30% (và hồi máu tài xế), +20 đạn theo xác suất. Bảng tổng kết có ô "mảnh xe" và thành tích "🔧 Tìm được n mảnh xe".

### v4.9 — Xuống xe bất cứ lúc nào, xe nổ/lật thì tài xế chạy tiếp, súng bắn cả xác xe
- Yêu cầu (kèm ảnh xe máy hết xăng đứng trước xác xe 75% mà súng không bắn): xe hết xăng còn đạn sao không bắn; cho xuống xe bất cứ lúc nào; xe đậu ngay nhà không xuống/không lục được; xe nổ thì tài xế nhảy xuống chạy tiếp, dùng súng lục bắn zombie hoặc lục soát nhà.
- Nguyên nhân không bắn: súng yếu (sát thương < 2, không nổ) bị cài bỏ qua xác xe. Nay mọi vũ khí đều bắn xác xe/vật cản.
- Nguyên nhân không lục được khi đậu sát cửa: nút hành động ưu tiên "LÊN XE" khi tài xế đứng cạnh xe. Nay ưu tiên "LỤC SOÁT" (tầm cửa 110), rồi mới "LÊN XE" (130).
- Xuống xe: nút XUỐNG XE luôn có khi xe chạy dưới 80 (không cần gần nhà, kể cả lúc lật). Tháp súng trên xe không bắn khi không có người.
- Xe hết xăng: không còn kết thúc lượt "Hết xăng!"; xuống xe đi tìm trạm xăng/lục nhà, hoặc đi bộ tới trạm (tới đích bằng chân vẫn tính vượt ải). Lượt chỉ kết thúc khi tài xế chết (quái, lô cốt, trùm, bão) hoặc rơi khỏi đường.
- Xe bị phá hủy (hoặc cháy trong bão): nổ "BÙMMM!", tài xế "NHẢY KHỎI XE!" chạy tiếp bằng súng lục, không lên lại được xác xe. Xe lật quá 3 giây: tài xế bò ra; lên lại thì tài xế lật xe đứng lại. Tài xế chết: ngã xuống, 1,6 giây sau ra bảng tổng kết ("Tài xế bị quái vật hạ gục!" / "bị vòng bo nuốt chửng!").
- Tài xế đi bộ bị vật cản chặn đường; khi không có quái gần, súng lục bắn vật cản / lô cốt phía trước (520) hoặc trùm (900). Trùm chuyển sang đánh tài xế: lại gần, ném đá/cầu lửa/plasma vào tài xế (12 + 3 x cấp), vả khi sát (14 + 4 x cấp mỗi giây).

### v5.0 — Đi bộ không giới hạn + nhảy, tiếng động cơ to hơn nhạc, đổi vị trí phím
- Yêu cầu (kèm ảnh tài xế đi bộ vẫn hiện "HẾT XĂNG!"): đi bộ vẫn báo hết xăng; đi một lúc không cho đi tiếp; nhạc/tiếng game lớn hơn tiếng động cơ; khi đi bộ cho di chuyển/nhảy; thêm tính năng đổi vị trí phím di chuyển/ga/phanh/lật ở màn hình chính và menu khi chơi.
- Bỏ giới hạn tài xế cách xe -900..+1400 (nguyên nhân "không cho đi nữa"); cảnh báo HẾT XĂNG chỉ hiện khi đang ngồi xe.
- Âm lượng: nhạc nền khi đang chạy 0,4 (trước 1,0); động cơ 0,45 + 0,4 x ga (+0,1 tên lửa) (trước 0,14 + 0,2 x ga).
- Đi bộ: nút NHẢY (ô tên lửa) bật -620, rơi theo trọng lực; đứng được trên nóc thùng/xác xe/lô cốt; vật cản chỉ chặn khi chân thấp hơn nóc (nhảy qua được). Rời xe có cú nhảy nhỏ.
- Đổi vị trí phím (State.LAYOUT): nút "🎮 PHÍM" ở màn hình chính và "🎮 ĐỔI VỊ TRÍ PHÍM" trong menu tạm dừng; kéo 6 ô (PHANH, ↺/◀, ↻/▶, 🚀/NHẢY, GA/LỤC, XUỐNG XE) tới chỗ mới; MẶC ĐỊNH / XONG. Lưu tâm ô theo phần nghìn màn hình ở khóa cx0..cx5, cy0..cy5 (-1 = chỗ mặc định); ô dùng chung cho lúc lái xe và lúc đi bộ.

### v5.1 — Lá chắn khi đi bộ, bão khi đi bộ, 3 thanh âm lượng, vào nhà đánh zombie, kho vũ khí phụ
- Yêu cầu: (1) đi bộ nhặt lá chắn không có tác dụng; (2) bão chạy qua lúc đi bộ mà không mất máu, tới vạch đích mới chết; (3) nhạc và hiệu ứng vẫn to hơn động cơ → thanh kéo chỉnh riêng 3 loại âm thanh; (4) lục soát nhà: thay vì đứng ở cửa thì vào một map riêng trong nhà, nhận vật phẩm và đánh zombie bằng vũ khí phụ; làm kho vũ khí phụ (súng lục, cung tên, giáo, phi tiêu…).
- Lá chắn / sửa xe / nhân bản khi đi bộ: tác dụng lên tài xế (Driver.shieldT, cloneT). Lá chắn 6 giây chặn mọi sát thương trừ bão, quái chạm vào bị hất văng và mất 1,5 máu (dơi nổ); có bong bóng xanh quanh tài xế và 🛡 trên HUD. Sửa xe khi đi bộ hồi 40% máu tài xế. Nhân bản: tài xế gục thì sống lại 60% máu (gục vì bão thì được đặt lên trước bão 600).
- Bão khi đi bộ: thực ra tài xế vẫn mất máu, nhưng HUD (thanh máu, "Bão cách …m", quãng đường, màn, chấm tiến độ) đều tính theo CHIẾC XE bỏ lại nên người chơi tưởng không mất máu. Nay HUD theo tài xế khi đi bộ (Game.playerX; thanh máu ♥ màu xanh dương là máu tài xế, ẩn đồng hồ tốc độ), màn hình chớp tím + hạt tím khi ở trong bão, cảnh báo "⚠ ĐANG Ở TRONG BÃO!".
- Âm thanh: State.SOUND "ÂM THANH" với 3 thanh kéo 0–100% (Nhạc nền, Hiệu ứng, Động cơ xe); mở bằng nút "🔊 ÂM THANH" ở màn hình chính (thay 2 nút 🔊 🎵 cũ) và "🔊 CHỈNH ÂM THANH" trong menu tạm dừng (thay 2 nút bật/tắt). Thả thanh Hiệu ứng phát tiếng xu để nghe thử; giữ thanh Động cơ thì nghe tiếng máy. Mặc định nhạc 50, hiệu ứng 65, động cơ 100 (bản cũ đã tắt thì về 0). Lưu khóa volMusic, volSfx, volEngine; Sfx.fxVol, Sfx.engineMaster, Music.master.
- Vào nhà (Interior.kt): đến cửa nhà chưa lục bấm "VÀO NHÀ" → màn trong nhà riêng: 2–4 phòng (rộng 900, trần 560) tùy loại nhà, tên phòng (phòng khách, bếp, kho hàng, xưởng sửa…), tường giấy dán/gạch men/gạch, cửa sổ đóng ván nhìn ra ngoài, đèn treo nhấp nháy, cửa "LỐI RA". Đồ đạc (bàn, kệ, sofa, quầy, cầu nâng xe, tủ lạnh, giường) đứng lên được (nhảy lên từ dưới). Rương: mỗi phòng 1 (tủ, thùng, hộp đồ nghề, tủ lạnh, tủ thuốc, két sắt) + 1 trên nóc kệ (phải nhảy). Bấm "LỤC" 1,1 giây (kỹ năng lục soát nhanh x2), bị cắn thì phải lục lại. Zombie trong nhà min(9, 1 + số phòng + ải/4) theo vùng (không có dơi), ngủ đến khi tài xế cách 650, cắn 7 + 0,6 x ải máu/giây; mở rương 45% có zombie phục kích (tối đa 1 + ải/6 lần).
- Đồ trong rương: tiền (25 + 10 x ải) x 0,7..1,3 (tạp hóa x1,5, két x3); mảnh xe 22% (gara 45%, hộp đồ nghề +20%, kỹ năng +15%); ngọc 35% 2–4 (két luôn 4–8); xăng (trạm xăng rương đầu, còn lại 15%); máu (tủ lạnh/tủ thuốc, nhà thuốc rương đầu, còn lại 15%; hồi tài xế 40% hoặc đầy + xe 15/30%); +15 đạn 25%; 6% (két 25%) nhặt được vũ khí phụ chưa có. Lục sạch +2♦, nhà thành "đã lục"; ra ngoài sớm thì vào lại tiếp được (biển "RƯƠNG x/y" trên nhà).
- Khi ở trong nhà: ngoài đường đứng yên trừ bão; bão tràn qua nhà thì trong nhà có màu tím và tài xế mất 45 máu/giây ("⚠ BÃO TRÀN VÀO NHÀ! RA NGOÀI CHẠY!"); xe đậu ngoài bị bão đốt cháy hỏng. HUD trên: "🏠 TÊN NHÀ — TÊN PHÒNG • RƯƠNG x/y • 🧟 n".
- Kho vũ khí phụ (SideWeapons.kt, tab thứ 5 trong gara — nút tròn hình cung + giáo): mua bằng tiền, trang bị 1 món, dùng khi đi bộ ngoài đường và trong nhà, tự nhắm, không tốn đạn. Lưu khóa ownedSide (bit), side.

| Vũ khí phụ | Mở ở ải | Giá | Sát thương | Nạp (giây) | Tầm | Đặc biệt |
|---|---|---|---|---|---|---|
| Súng lục | có sẵn | 0 | 1,5 | 0,32 | 700 | — |
| Phi tiêu | có sẵn | 520 | 1,1 | 0,17 | 560 | Bay cong (trọng lực 520) |
| Giáo | 1 | 1100 | 3,4 | 0,5 | 175 | Cận chiến, trúng mọi con trong tầm, đẩy lùi |
| Cung tên | 2 | 1600 | 3,6 | 0,62 | 950 | Bay cong, xuyên 2 |
| Rìu chiến | 4 | 2900 | 6,5 | 0,72 | 125 | Cận chiến, hất văng mạnh |
| Súng săn | 6 | 4500 | 1,15 x 5 | 0,8 | 460 | Chùm 5 viên |
| Tiểu liên | 9 | 7800 | 1,0 | 0,1 | 700 | — |

- Kiểm tra (tools/dev/preview `gradle inside`): bot vào nhà ở ải 1/7/12/18, mở hết 3–4 rương (cả rương trên kệ), hạ 4–10 zombie, ra ngoài, nhà thành đã lục; `gradle walk`: tài xế đứng yên bị bão nuốt sau ~12 giây; `gradle drive` ải 5/10/20 vẫn tới đích.

### v6.0 — Chuỗi 30 phòng nhà kho kiểu Earn to Die Rogue
- Yêu cầu: người dùng gửi video Earn to Die Rogue (lái xe → xe cháy → đi bộ tới xe lái buôn chọn hộp quà → nhà kho "Room 1/30", bục vàng-đen nhiều tầng, cần gạt + nút nhảy, súng tự bắn, tiền xanh rơi, thanh Lvl → "Select Powerup" 1 trong 3, "Select Upgrade" Damage+/Heal+, trùm tháp pháo) và muốn biến game thành như vậy. Người dùng chọn: GIỮ cả lái xe; 30 phòng như video; cần gạt + nút nhảy.
- Vòng chơi mới mỗi ải: lái xe vượt bão như cũ → khi xe tới trạm (hoặc tài xế đi bộ tới trạm) HOẶC xe bị phá hủy (1,8 giây sau) thì vào chuỗi phòng (Rooms.kt). Tới trạm: đủ máu + 2 hộp lái buôn; xe hỏng: máu theo tài xế (tối thiểu 70%) + 1 hộp. Qua phòng 30 = vượt ải (stageComplete như cũ). Gục trong phòng = hết ngày ("Gục ở phòng n/30"). Trong phòng không có bão.
- Máu tài xế trong phòng: 100 + 4 x ải + 0,5 x tổng kỹ năng Máu (+25 mỗi cấp Máu+).
- Phòng: rộng 1900–2460 (phòng trùm 2000), sàn y=0, bục chắn vàng-đen một chiều ở độ cao 150/290/420 (nhảy -700, tối đa ~270), đồ trang trí (kệ hộp, xác xe, thùng, pallet), cửa vào trái, cửa EXIT phải (biển đỏ "KHÓA" + ván đóng, mở thì xanh "EXIT ➜" khi diệt hết quái); bước vào cửa mở = sang phòng sau (màn tối dần). Phòng >3 có 40% đợt quái thứ 2 từ hai cửa. Màu trời qua cửa sổ theo vùng.
- Quái: min(14, 3 + phòng/3 + ải/3), 40% đứng trên bục (rơi xuống khi đi ra mép), máu = gốc x 1,6 x (1 + 0,06 x (ải-1) + 0,03 x phòng); loại theo vùng (đêm có dơi bay bổ nhào); 22% là quái phun axit xanh (đứng cách 420, phun vòng cung 6 + 0,6 x ải). Cắn 8 + 0,7 x ải + 0,15 x phòng máu/giây.
- Trùm: phòng 10 THÁP PHÁO (60 máu x hệ số, bắn quạt 3 cầu lửa mỗi 2,2 giây), phòng 20 ĐỒ TỂ (80, đi chậm rồi húc lao 620 trong 0,9 giây), phòng 30 trùm vùng (120, vẽ bằng hình trùm đường đua: khổng lồ/rồng/dơi/ngoài hành tinh; ải 5/10/15/20 dùng đúng trùm ải). Hạ trùm: nổ, 10 cọc tiền, +10 XP, bảng CHỌN NÂNG CẤP (Sát thương+ hoặc Máu+).
- Tiền: quái rơi tiền giấy xanh (nửa giá thưởng quái), tự bay vào người trong 170 (x2/x3 với Nam châm); 5% rơi tim +15 máu. Tiền cộng vào runMoney.
- Cấp trong lượt: XP 1/quái (béo/nhớt 2, trùm 10), cần 4 + 3 x cấp; lên cấp → State.PICK "LÊN CẤP n!" chọn 1/3 sức mạnh. Lái buôn ("LÁI BUÔN — chọn 1 hộp quà bí ẩn", 3 hộp ??????) ở đầu lượt và sau phòng 5, 15, 25.
- 15 sức mạnh (Power, cộng dồn tới cấp tối đa): Đạn kép (+1 viên/phát, 4), Thuốc nổ (tự ném mỗi 3,4/(1+0,15 cấp) giây, nổ 150 + 15 x cấp, 4 + 2 x cấp, 5), Que diêm (vùng lửa 120 + 20 x cấp đốt 1,2 máu/giây, 5), Nitơ lỏng (trúng đạn chậm 25%/cấp 1,6 giây, 3), Xác nổ (quái chết nổ 120, 2 + 1,5 x cấp, 5), Giày chạy (+15%/cấp, 3), Mảnh sắt quay (n mảnh bán kính 115, 2 + cấp, 4), Adrenaline (hạ quái bắn nhanh x1,5 trong 2 + cấp giây, 3), Pin xe (sét mỗi 2,5 giây nhảy 2 + cấp quái, 2 + 1,2 x cấp, 5), Sát thương+ (+25%, 6), Tốc bắn+ (+20%, 5), Máu+ (+25 máu tối đa, hồi 25%, 6), Nam châm (2), Nhảy đôi (1), Hút máu (hạ quái hồi 1,5 x cấp, 3). Vũ khí là vũ khí phụ đang trang bị (tự nhắm mọi hướng, tầm +150).
- Điều khiển đi bộ (ngoài đường, trong nhà và trong phòng): CẦN GẠT nổi — chạm bất kỳ đâu nửa trái màn hình (không trúng nút) để đặt cần, kéo trái/phải để đi (ngưỡng 22); nút NHẢY (ô tên lửa); ngoài đường còn ô GA = VÀO NHÀ/LỤC/RA NGOÀI/LÊN XE. Bỏ 2 ô ◀ ▶ khi đi bộ.
- HUD phòng: thanh "Cấp n" + XP trên cùng, "🚪 Phòng n / 30" (👹 phòng trùm, chữ "PHÒNG TRÙM!"), máu ♥ số, tiền xanh, hàng biểu tượng sức mạnh có số cấp.
- Kiểm tra (tools/dev/preview `gradle rooms`, thêm `-Pa=god` để bất tử): bot bất tử qua đủ 30 phòng ở ải 1/6/10 (~4 phút/lượt, cấp 12–15, 250–330 quái); bot thường qua ải 1 (còn 9 máu), ải 6 (còn 105/199), gục ở phòng 10 ải 10. `gradle drive`: tới trạm thì vào lái buôn (State.PICK).
- Còn có thể làm tiếp theo video: màn hình chính dạng bản đồ vùng có thanh tiến độ, nhiệm vụ hằng ngày, thành tích, quà miễn phí, nhân vật lái buôn vẽ hình, phòng nghỉ có bàn đồ nghề, cây kỹ năng dài vô tận mở bằng tiền.

### v6.1 — Lái ngắn rồi vào kho, màn hình chính kiểu bản đồ, lái buôn, nhiệm vụ ngày, thành tích, phòng nghỉ
- Phản hồi người dùng: "cài 6.0 rồi mà chơi không thay đổi gì mấy" — vì kho chỉ mở khi tới trạm/xe nổ; hết xăng/lật xe thì vẫn đi bộ trên đường như cũ; menu/gara không đổi; không có số phiên bản. Người dùng chọn: lái ngắn rồi vào kho; làm cả 4 phần giống video (bản đồ, lái buôn có hình, nhiệm vụ + thành tích, phòng nghỉ).
- Đường ngắn lại: stageGoalM = 600 + 30 x (ải - 1) (ải 1 = 600m ~26 giây ga hết cỡ, ải 20 = 1170m). Hết xăng và xe dừng (< 40) quá 1,5 giây → "HẾT XĂNG! CHẠY VÀO KHO HÀNG"; lật quá 3 giây → "LẬT XE! CHẠY VÀO KHO HÀNG" (thay cho bò ra đi bộ). Tự xuống xe đi bộ lục nhà ven đường vẫn như cũ.
- Màn hình chính (drawMenu mới, giống video): 5 nút tròn bên trái (Nhà đang sáng, Xe, Vũ khí, Kỹ năng, Vũ khí phụ → mở gara đúng tab), thanh trên (sao cấp + XP, ngọc, tiền), băng tên "🗺 ẢI n: TÊN MAP ▾" (chạm → chọn map), đảo nổi có núi, biển số ải (👹 ải trùm), xác xe và tài xế (chạm đảo → chọn map), thanh tiến độ 5 ải của vùng có đầu lâu 💀, dòng "vùng • kiểu đường • m + 30 phòng • kỷ lục", nút vàng to "CHẠY!", cột phải: 📋 NHIỆM VỤ (chấm đỏ số việc nhận được), 🏆 THÀNH TÍCH, 📖 CỐT TRUYỆN, 🔊 ÂM THANH, 🎮 PHÍM, ↺ CHƠI LẠI; "v6.1" góc dưới trái. Bỏ nút GARA và bảng xếp hạng ở menu (bảng xếp hạng chuyển vào tab XẾP HẠNG).
- Nhiệm vụ & thành tích (Missions.kt, State.MISSIONS, 3 tab NHIỆM VỤ NGÀY / THÀNH TÍCH / XẾP HẠNG): bộ đếm trọn đời SaveData.life theo Stat (zombie, phòng, trùm, tiền, ải, ngọc, mét, nhà lục sạch). 3 nhiệm vụ mỗi ngày (đổi lúc 0 giờ giờ VN, Random(ngày)), mỗi cái 1 trong 8 loại x 3 mức (vd hạ 40/90/160 zombie, qua 10/25/45 phòng, hạ 1/2/3 trùm, kiếm 500/1500/3000$…), thưởng 5–20 ngọc; tiến độ = bộ đếm - mốc lúc nhận. 16 thành tích (100/1000/5000 zombie, 30/300/1000 phòng, 3/30 trùm, vượt ải 1/5/10/20, 10k/100k $, 10 nhà, 20km), thưởng 10–150 ngọc. Lưu khóa life0.., mDay, mKind/mTier/mBase0..2, mClaimed, achClaimed. Ghi số: phòng khi bước qua cửa, trùm khi hạ (cả trùm đường), nhà khi lục sạch, còn lại cộng lúc kết thúc lượt (finishStats).
- Lái buôn có hình (drawMerchant): ông to con khăn đỏ, râu, áo gile xanh, giáp vai gai, tay móc câu, khung thoại "Chọn 1 món đi, nhóc! Hàng tốt cả đấy."
- Phòng nghỉ: phòng 7, 14, 21, 28 không có quái, cửa mở sẵn, giữa phòng có BÀN ĐỒ NGHỀ (bảng dụng cụ, hộp đồ, đèn bàn); bước tới bàn → bảng "BÀN ĐỒ NGHỀ" chọn 1: Băng bó (hồi 50% máu), Mài vũ khí (+1 Sát thương+, đầy thì +1 Tốc bắn+), Đổi vũ khí (cầm vũ khí phụ kế tiếp đã có; chưa có thì +1 Đạn kép). Mỗi phòng nghỉ dùng 1 lần. HUD ghi "• PHÒNG NGHỈ".
- Kiểm tra: `gradle drive` ải 1/3 tới trạm sau 26–31 giây rồi vào lái buôn; `gradle rooms -Pa=god` qua 30 phòng ở ải 1/6/10 (có phòng nghỉ); `gradle inside` chụp menu mới, nhiệm vụ, thành tích.

### v7.0 — LÀM LẠI: thủ trại sinh tồn
- Yêu cầu: "Bỏ kiểu chạy bo đi, làm y như video sinh tồn. Khi thu thập đủ mảnh cho xe/súng/pháo đài/bệ phòng thủ thì hẵng chạy, chạy tới đâu phòng thủ tới đó. Làm lại từ đầu hết." Người dùng chọn: THỦ TRẠI LÀ CHÍNH; khi phòng thủ CHỈ DỰNG BỆ (tower defense, nhân vật không đánh); đồ họa dùng ẢNH AI từ script (chưa có ảnh — game tạm vẽ bằng code); BẮT ĐẦU LẠI TỪ ĐẦU (lưu mới prefs "dxst_v7").
- Kèm theo: sửa script Python cho Google Colab tạo asset (tools/assets/tao_asset_colab.py): key lấy từ Colab Secrets/getpass, lưu Google Drive "MyDrive/DuaXeSinhTon_assets" (chạy lại bỏ qua file đã có), Recraft style digital_illustration (vector_illustration trả SVG — lỗi của script gốc), xóa phông fal.ai BiRefNet, ElevenLabs sound-generation; 101 hình (nhân vật, zombie, xe, mảnh, vũ khí, bệ phòng thủ, vật phẩm, sức mạnh, giao diện, nền) + 30 âm thanh/nhạc; tự thử lại khi lỗi; nén zip. Khi có asset: người dùng để trên Drive, trợ lý tải về (connector Google Drive) và nạp vào game.
- VÒNG CHƠI (Campaign.kt, CampDraw.kt): menu "VÀO TRẠI!" → State.CAMP (ngày). Mỗi ngày chọn 1 việc: 🔍 LỤC SOÁT (6 phòng nhà kho kiểu video, có lái buôn, sức mạnh, rương) hoặc 🚗 LÊN ĐƯỜNG (cần ≥ 3L xăng); hoặc bỏ qua. Rồi 🌙 VÀO ĐÊM → State.SIEGE: zombie từ phải tràn sang, bệ tự bắn. Thắng đêm → ngày mới (+tiền giết quái, +phế liệu 15 + 4 x đêm, +ngọc 2 + đêm/3). Tường vỡ → "TRẠI THẤT THỦ": mất rào/bệ dưới đất, mất nửa phế liệu, tường còn 50%, sang ngày mới. Đích: đi đủ 12.000m tới trạm cứu hộ (VICTORY). Không còn bão, ải, trùm đường, nhà ven đường.
- Tài nguyên: tiền $ (giết quái), ⚙ phế liệu (dựng/nâng/sửa bệ; khởi đầu 120), ⛽ xăng (lít = giây ga; khởi đầu 12L), ♦ ngọc (cây kỹ năng), mảnh ghép theo bản thiết kế.
- Trại (nhìn cố định x -150..2450, zoom W/2600, mặt đất 80% màn hình): lều, xe hiện tại, thùng lửa, chòi canh, TƯỜNG ở x 700 (gỗ 320 / sắt 650 / bê tông 1100 máu; cao 230/260/290). 7 ô: 0 = trên tường, 1 = chòi canh (chỉ đặt súng), 2–6 = trước tường x 920..1680 (đặt được mọi thứ). Khởi đầu có Súng máy trên tường và Rào gỗ ô 2. Chạm ô → bảng dựng (2 hàng x 4); chạm bệ → NÂNG CẤP (tối đa 3 sao, giá = giá gốc x (cấp+1)/2 + 20, máu/sát thương +50%/cấp), SỬA, BÁN (hoàn 50%); chạm tường → SỬA TƯỜNG. Dựng được cả trong đêm.
- 8 loại bệ (DefType; ⚙ giá, máu, tầm, sát thương, nạp): Rào gỗ 25 (160, chặn), Súng máy 60 (90, 760, 1,3, 0,16 giây), Rào gai 45 (220, chặn + cứa 7/giây), Pháo 120 (980, 9, 1,6 giây, nổ 120), Súng lửa 110 (420, 0,8 mỗi 0,09 giây + đốt), Tesla 140 (500, sét 3 con 4,5 + làm chậm), Cối 130 (300–1600, 12, 2,6 giây, nổ 170), Mìn 30 (nổ 1 lần 30, bán kính 160). Rào gỗ & súng máy có sẵn; 6 loại còn lại phải đủ mảnh rồi lắp ở XƯỞNG.
- Đêm n: số đợt min(6, 3 + n/3), mỗi đợt 5 + 2n + 2 x đợt + 3 x vùng con, cách 5 giây; quái theo vùng (dơi bay qua rào đánh thẳng tường; 18% phun axit đứng xa 380); máu = gốc x 7 x (1 + 0,2n + 0,3 x vùng); cắn 7 + 0,9n /giây (béo x1,8). Đêm chia hết cho 5 có TRÙM (đồ tể to, 300 x (1 + 0,25n) máu, cắn x4). Quái chặn bởi bệ dưới đất xa nhất (trừ mìn) rồi tới tường.
- XƯỞNG (State.WORKSHOP, nút 🔧 XƯỞNG hoặc nút tròn Xe ở menu): 12 bản thiết kế (Blueprints): 4 xe (Xe con gỉ sét 5 mảnh/300$, Bán tải 6/1200, Bọc thép 7/2700, Xe tăng 8/4800 — dùng Vehicles.of(dòng, 0)), 6 bệ (Rào gai 3, Mìn 3, còn lại 5 mảnh; giá = giá ⚙ x 3 $), 2 tường (sắt 5/600$, bê tông 7/1500$). Lưu bp0.., bpOwned, carTier, wallLv.
- LỤC SOÁT: RoomRun(total = 6, trùm phòng cuối nếu ngày chia hết 3), mỗi phòng 2–3 rương (một số trên bục, phải nhảy); chạm rương: +4..11 + đêm/2 ⚙, 40% +2..4L xăng, 35% 1 mảnh (thiết kế còn thiếu, ưu tiên xe 35%). Về trại khi qua cửa phòng 6; gục thì giữ nửa phế liệu/xăng, mất mảnh. Phòng nghỉ (phòng 7, 14…) không xuất hiện trong chuyến 6 phòng.
- LÊN ĐƯỜNG: lái xe (vật lý cũ, quái/vật cản/lô cốt trên đường, đoạn tối đa 1500m), bình = min(bình xe, xăng có). Hết xăng & dừng, lật 3 giây, xe nổ, rơi, hoặc hết đoạn → dựng trại mới tại chỗ: +mét đi được, xăng bị trừ, bệ dưới đất bỏ lại, tường đầy máu. Vùng theo quãng đường: Sa mạc 0–3000m, Núi lửa 3000–6000, Rừng đêm 6000–9000, Hành tinh lạ 9000–12000; độ khó đường zone = 1 + mét/600.
- Menu: nút "🏕 NGÀY n • VÙNG", đảo nổi có biển "x km", thanh hành trình tới 🏁, dòng "Đã sống sót n đêm • Đã đi Xm / 12000m • ⚙ • ⛽", nút VÀO TRẠI!; nút tròn Xe → Xưởng. Nhiệm vụ/thành tích đổi: "Sống sót n đêm", "Lục soát xong n chuyến", "Tới trạm cứu hộ (12km)".
- Kiểm tra: tools/dev/preview `gradle camp` (trại → dựng súng → 3 đêm → lục soát 6 phòng → đêm 4 → lên đường 403m → trại mới → xưởng). Các bài thử cũ `inside`, `rooms`, `drive`, `walk` dùng luồng cũ (tap0 → ải), nay không còn đúng.
- Còn làm tiếp: nạp ảnh/âm thanh AI khi người dùng tạo xong; cân bằng độ khó sau khi chơi thử (đêm 1–2 hiện khá dễ); vẽ trại theo vùng (núi lửa/đêm/ngoài hành tinh); bỏ code cũ không dùng (bão, nhà ven đường, ải, gara xe cũ).

### v7.1 — Ảnh/âm thanh AI
- Script tạo asset đổi sang MIỄN PHÍ (Recraft bắt mua token): ảnh FLUX.1-schnell qua gradio_client (Space black-forest-labs/FLUX.1-schnell, api "/infer", tham số prompt/seed/randomize_seed/width/height/num_inference_steps=4; ảnh vuông 1024x1024, ngang 1344x768), xóa phông rembg (session "isnet-general-use") + tự cắt sát vật thể chừa 8px; nền & biểu tượng sức mạnh giữ phông; âm thanh ElevenLabs (không bắt buộc). Secrets Colab: HF_TOKEN (khuyên dùng, nhiều lượt GPU hơn), ELEVENLABS_API_KEY. Hàm lay_key báo lỗi nếu ô Value có dấu cách/xuống dòng (người dùng từng dán nhầm đoạn code mẫu của Recraft vào Value). Hết lượt GPU (lỗi "quota/exceeded") thì dừng, chạy lại sẽ tiếp tục (bỏ qua file đã có). Bản người dùng đã chạy được lưu ở tools/assets/tao_asset_colab.py: cài thư viện ở ô riêng (!pip install -q gradio_client rembg onnxruntime), CHAY_THU = True, thư mục Drive "MyDrive/Colab_Notebooks/DuaXeSinhTon_assets".
- Drive của người dùng: thư mục https://drive.google.com/drive/folders/14i1_kbJc9H1rZfqnH9T_pj3xFs0gynEw chứa DuaXeSinhTon_assets/, DuaXeSinhTon_assets.zip, notebook 2d-game-car.ipynb, tài liệu "code asset google lab" và bảng "Key API" (CHỨA KEY — KHÔNG mở/đọc). Trợ lý tải zip bằng connector Google Drive (download_file_content → file lưu vào tool-results), giải nén vào thư mục tạm, rồi chạy: python3 tools/assets/nhap_asset.py <thư mục DuaXeSinhTon_assets>.
- Lần nhập đầu (bộ chạy thử, mỗi nhóm 1 file): survivor_idle, zombie_walker, car_motorbike, part_engine, wpn_pistol, def_barricade_wood, item_cash, pw_dual_magazine, ui_skull, bg_desert_far + sfx_pistol.mp3, music_menu.mp3. Nhận xét: chất lượng tốt, phông tách sạch; zombie quay mặt PHẢI (FLUX bỏ qua "facing left") — game coi mọi sprite là quay phải rồi lật; def_barricade_wood ra hình cột gỗ (nên tạo lại với mô tả khác).
- tools/assets/nhap_asset.py: sprite thu nhỏ tối đa 384px PNG → app/src/main/assets/img/<tên>.png; nền (nhóm nen) 1280px JPEG q85 → img/<tên>.jpg; âm thanh → assets/sfx/, nhạc → assets/music/. build_apk.sh thêm "-A app/src/main/assets" cho aapt2 (Gradle trên CI tự đóng gói).
- Game nạp theo TÊN (Sprites.kt): có file thì vẽ ảnh, thiếu thì vẽ bằng code như cũ — tạo thêm asset chỉ cần nhập là tự dùng. Sprites.open do MainActivity đặt (assets.open); SpriteDraw.kt: sprite(c, tên, cx, đáy, cao, lật), spriteBox(...), backdrop(...). Chỗ dùng: zombie (zombie_walker/runner/fat/bat, lật theo hướng người chơi; trùm đêm boss_brute), tài xế (survivor_idle / survivor_run khi chạy, cả ở menu), bệ trại (def_barricade_wood/spikes, def_turret_mg/cannon/flame/tesla/mortar, def_landmine), nền trại theo vùng (bg_desert_far, bg_city_ruins, bg_night_forest, bg_fortress), xe đậu ở trại (car_motorbike/sedan/pickup/bus/tank theo cấp xe), tiền rơi (item_cash), rương lục soát (item_crate — v7.0 quên vẽ rương, nay có hình hộp gỗ thay thế), thẻ & HUD sức mạnh (pw_*), thẻ vũ khí phụ (wpn_*), ông lái buôn (merchant), xưởng (ảnh xe/bệ + part_engine/part_gear/part_brick), dấu trùm ở menu (ui_skull). Âm thanh: Sfx.ASSET thay tiếng tổng hợp khi có mp3 (SHOOT←sfx_pistol, nổ←sfx_explosion, xu←sfx_coin…); nhạc mp3 CHƯA nối (Music vẫn tổng hợp).
- Việc tiếp: người dùng đặt CHAY_THU = False để tạo đủ ~100 ảnh rồi trợ lý nhập lại; nối nhạc mp3 (MediaPlayer); tạo lại def_barricade_wood.

### v7.2 — Bộ asset đầy đủ (SDXL-Turbo trên GPU Colab), nhạc mp3 (hiện tại)
- Người dùng chuyển script sang vẽ ảnh NGAY TRÊN GPU COLAB bằng SDXL-Turbo (diffusers, 2 bước, guidance 0, ảnh 512x512 / 768x512) + rembg + ElevenLabs; lưu ở tools/assets/tao_asset_colab_gpu.py (cài ô riêng: !pip install -q diffusers transformers accelerate rembg onnxruntime). Đã tạo đủ 101 ảnh + 30 âm thanh/nhạc trên Drive.
- Zip trên Drive (~37MB) vượt giới hạn 10MB của connector → tải TỪNG FILE: search_files (mimeType image/png hoặc audio/mpeg, modifiedTime > …, owner = me, phân trang) lấy id; download_file_content từng id (kết quả lưu JSON base64 trong tool-results; file nhỏ trả về thẳng) rồi giải mã. Giao việc tải hàng loạt cho subagent để không tốn ngữ cảnh chính. Chỉ tải 72 file game đang dùng (zombie 4 + boss_brute, survivor_idle/run, merchant, 9 ảnh phòng thủ, 5 xe, item_cash/crate, 15 pw_*, 7 wpn_*, 4 nền, part_engine/brick/gear, ui_skull, 3 nhạc, 15 sfx); các file khác vẫn ở Drive.
- nhap_asset.py nay giảm PNG còn 256 màu (giữ alpha) → img ~2MB thay vì ~10MB.
- Nhạc mp3: Music phát music_menu (CHILL) và music_battle (INTENSE) bằng MediaPlayer lặp, mờ dần như cũ; VICTORY vẫn tổng hợp. Preview có MediaPlayer giả (im lặng).

## 3. Luật chơi cũ trước v7.0 (v6.1, để tham khảo)

### Điều khiển
| Nút | Tác dụng |
|---|---|
| GA (góc phải dưới, hoặc chạm nửa phải màn hình) | Tăng tốc, tốn xăng |
| PHANH (trái) | Phanh / lùi |
| ↺ ↻ (trái dưới) | Nghiêng xe ra sau / ra trước (mạnh hơn khi đang bay) |
| 🚀 | Tên lửa đẩy, có bình riêng |
| Vũ khí trên nóc | Tự bắn zombie/vật cản phía trước khi còn đạn |
| II (phải trên) | Tạm dừng; nút Back cũng tạm dừng |
| XUỐNG XE | Khi xe chạy dưới 80; đi bộ bằng ◀ ▶, ô 🚀 thành NHẢY, ô GA thành VÀO NHÀ / LỤC / RA NGOÀI / LÊN XE; vũ khí phụ tự đánh |

### Vòng chơi
- Mỗi lần chạy là một ngày. Ván kết thúc khi: hết xăng và đứng yên 2 giây, xe bị phá hủy (nổ), lật/kẹt quá 3 giây, rơi khỏi đường, bị bão nuốt, hoặc bỏ cuộc.
- Tiền nhận: 0,25$ mỗi mét + tiền thưởng (zombie/quái và vật cản x (1 + 0,12 x (ải - 1)), tiền nhặt, qua mốc màn, bay xa trên 1,2 giây). Tới trạm cứu hộ thêm 120$ x số ải.
- Điểm = mét x 10 + zombie x 100 + vật cản x 50 (+ 5000 x số ải nếu tới trạm).
- Đường mỗi ải dài 700 + (ải-1) x 100 mét (ải 1 = 700m, ải 20 = 2600m), chia 5 màn bằng cột mốc; qua mốc thưởng 10 x ải x số màn.

### Vòng bo (bão tím đuổi phía sau)
| Giai đoạn | Thời gian | Tốc độ bão (đơn vị/giây) |
|---|---|---|
| Bo 1 | 60 giây | 150 |
| Bo 2 | 20 giây | 450 |
| Bo 3 | 5 giây, đếm ngược 5-4-3-2-1 có tiếng bíp | 1100 |
| Đã khép | — | 2600 |
- Bão bắt đầu cách xe 50m phía sau. Ở trong bão mất 80 máu/giây (lá chắn không chặn được).
- Vật phẩm ⏱ cộng 5 giây cho giai đoạn hiện tại và đẩy bão lùi 400 đơn vị.

### Độ khó
- Mỗi 100m có 3 + (màn - 1) zombie/vật cản, tối đa 20 (màn tính liên tục: ải 2 màn 1 là màn số 6).
- Khi mật độ trên 10, tỉ lệ vật cản giảm xuống 30% (còn lại là zombie) để đường còn chạy được.
- Zombie béo và xác xe xuất hiện từ ải 2; chồng thùng cao hơn theo ải.
- Địa hình: biên độ đồi = min(230, 75 + 8 x ải); độ gồ ghề tăng đến ải 20.

### Zombie, vật cản, vật phẩm
| Loại | Thông số chính |
|---|---|
| Zombie thường / chạy nhanh / béo | Máu 2 / 1 / 5; thưởng 5 / 6 / 12$; tông trên 120 đơn vị/giây thì văng đi, chậm hơn thì zombie bám cào xe (10 máu/giây) |
| Thùng gỗ | Phá ngay nếu tốc độ trên 260; sát thương 4 |
| Thùng phuy | Luôn nổ, bán kính 220, sát thương xe 10, gây nổ dây chuyền |
| Xác xe | Phá ngay nếu tốc độ trên 720; sát thương 18; máu với đạn 8 |
| Vật phẩm | $ tiền (5 + ải), ⛽ +25% xăng, ✚ +30% máu, 🛡 lá chắn 6 giây, 🎯 +15 đạn, ⏱ +5 giây & đẩy bão, 👥 nhân bản (hồi sinh 1 lần trong 10 giây, mở sau ải 5) |

### Nâng cấp cũ bằng tiền (v4.0–v4.5) — đã thay bằng nâng cấp bằng ngọc đỏ từ v4.7 (bảng giữ để tham chiếu công thức hoàn tiền và tune của xe)
| Nâng cấp | Base | Tác dụng |
|---|---|---|
| Động cơ | 150 | Tốc độ tối đa 700 + 110/cấp; lực kéo 520 + 80/cấp |
| Bình xăng | 120 | 22 + 6 giây ga/cấp |
| Tên lửa đẩy | 180 | 1,5 + 1,2 giây/cấp |
| Giáp & máu | 140 | Máu 100 + 30/cấp; giáp +6%/cấp (tối đa 70%) |
| Đạn dược | 100 | 20 + 20 viên/cấp |
| Lốp xe | 110 | Độ bám 1,0 + 0,15/cấp |

### Gara (v4.8: phòng xưởng, nút tròn Nhà/Xe/Vũ khí/Kỹ năng; xe mở bằng 5 mảnh + tiền; cây kỹ năng bằng ngọc đỏ) — 5 dòng xe x 5 phiên bản, giá đã +30%
- Dòng xe mở khóa theo ải đã vượt: Xe máy (có sẵn), Xe hơi (qua ải 5), Bán tải (qua ải 10), Bọc thép (qua ải 15), Xe tăng (qua ải 20).
- Chỉ số gốc của dòng (máu / tốc độ / lực kéo / giáp / xăng / bám / sức húc): Xe máy 0,75/1,04/0,95/0%/0,85/1,05/0,6; Xe hơi 1,0/1,10/1,05/5%/1,0/1,0/0,9; Bán tải 1,3/1,02/1,15/10%/1,2/1,08/1,15; Bọc thép 1,8/1,0/1,3/22%/1,3/1,1/1,45; Xe tăng 2,4/0,96/1,5/32%/1,45/1,25/2,0.
- Phiên bản k (0..4): máu +10%·k, tốc độ +3%·k, lực kéo +5%·k, giáp +2,5%·k, xăng +6%·k, bám +3%·k, sức húc +10%·k.
- Sức húc: chia ngưỡng phá vật cản (chỉ khi > 1, xe nhẹ giữ ngưỡng cũ), ≥ 1,4 thì mỗi cú húc trừ 50% độ bền; nhân sát thương tông trùm.

| Dòng | Phiên bản 1 → 5 | Giá ($, đã +30%) |
|---|---|---|
| Xe máy | Xe Cub Cũ, Cào Cào Địa Hình, Mô Tô Phân Khối Lớn, Chopper Đinh Tán, Mô Tô Chiến Binh | 0, 780, 1560, 2600, 4160 |
| Xe hơi | Xe Con Gỉ Sét, Xe Cơ Bắp, Xe Đua Rally, Siêu Xe Gai Nhọn, Chiến Xa Tử Thần | 2600, 3900, 5460, 7280, 9750 |
| Bán tải | Bán Tải Cũ, Bán Tải Săn Zombie, Bán Tải Lưới Thép, Bán Tải Off-road, Bán Tải Quái Thú | 6500, 8450, 11050, 14300, 18200 |
| Bọc thép | Xe Chở Tiền, Xe Đặc Nhiệm SWAT, Xe Bọc Thép Lội Nước, Xe Ủi Thép, Pháo Đài Di Động | 13000, 16900, 21450, 26650, 32500 |
| Xe tăng | Tăng Hạng Nhẹ, Tăng Sa Mạc, Tăng Rằn Ri, Tăng Hạng Nặng, Tăng Hủy Diệt | 23400, 29900, 37700, 46800, 58500 |

Vũ khí (10 loại, mỗi loại nâng 5 cấp: +18% sát thương, -6% thời gian nạp, +5% tầm, +6% bán kính nổ mỗi cấp; giá cấp = (giá gốc hoặc 400) x 0,35 x (cấp+1) + 150 x cấp², x1,3):

| Vũ khí | Mở ở ải | Giá | Sát thương | Nạp (giây) | Tầm | Đặc biệt |
|---|---|---|---|---|---|---|
| Súng lục | có sẵn | 0 | 1 | 0,34 | 760 | — |
| Dao chém | 1 | 650 | 2 | 0,3 | 190 trước mũi | Không tốn đạn, chém cả vật cản/trùm |
| Súng máy | 1 | 1950 | 1 | 0,14 | 900 | — |
| Nỏ | 2 | 3120 | 3 | 0,45 | 950 | Xuyên 3 (+1 mỗi 2 cấp) |
| Súng hoa cải | 3 | 4160 | 1,2 x 5 viên | 0,7 | 560 | Chùm 5 viên |
| Máy ném đá | 5 | 5850 | 4 | 0,8 | 900 | Nổ bán kính 160 |
| Súng phun lửa | 7 | 9100 | 0,55 | 0,07 | 400 | Xuyên mọi thứ, đốt cháy 2 giây (1,2 máu/giây), 0,3 đạn/lần |
| Tên lửa | 10 | 14300 | 7 | 1,3 | 1100 | Tự dẫn, nổ bán kính 210 |
| Súng laser | 13 | 20800 | 2,4 | 0,5 | 1150 | Tia tức thì xuyên tất cả |
| Súng nòng xoay | 16 | 28600 | 1,1 | 0,055 | 1000 | 0,5 đạn/lần |

### Thành tích 🏆 (hiện trong bảng tổng kết)
- Tới trạm cứu hộ; Không trầy xước (tới trạm với máu ≥ 90%); Kỷ lục quãng đường; Điểm cao nhất mới.
- Sát thủ zombie (≥ 20) / Đồ tể zombie (≥ 50); Xe ủi (≥ 15 vật cản); Bay cao (≥ 1 giây) / Phi công (≥ 2 giây); Tốc độ bàn thờ (≥ 150 km/h); Thoát bão trong gang tấc (bão cách dưới 8m).
- Khi vượt ải 1 / ải 5 còn hiện chip mở khóa Gara / Nhân bản; thắng ải 20 có chip "Huyền thoại sinh tồn".

## 4. Cấu trúc mã nguồn

| File | Vai trò |
|---|---|
| app/src/main/java/com/khongvinh/carsurvival/MainActivity.kt | Activity toàn màn hình, nằm ngang; onPause/onResume tắt mở nhạc; Back = tạm dừng |
| GameView.kt | SurfaceView + vòng lặp game (thread), cảm ứng đa điểm; canvas ảo cao 720 đơn vị |
| Game.kt | Luật chơi: trạng thái, ải/màn, sinh zombie/vật cản/vật phẩm, va chạm, vũ khí, bão, tiền, điểm, thành tích, nhạc theo trạng thái, nút bấm |
| CarPhysics.kt | Vật lý xe thuần Kotlin: 2 bánh lò xo, bám đường, nghiêng, tên lửa, tự cân bằng khi bay, va chạm thân xe với đất |
| Terrain.kt | Sinh địa hình theo RoadStyle (hầm, hang có trần, cầu qua vực): đồi nhiều tầng, đá, ổ gà, ván giặt, dốc nhảy, giới hạn dốc 38°, bo thung lũng, cảnh vật |
| Themes.kt | 4 vùng (Region) và 20 bản đồ (MapTheme): tên, màu trời/núi/đất/cỏ, kiểu đường chân trời |
| Renderer.kt | Vẽ toàn bộ: nền theo bản đồ (trời/núi/thành phố/núi lửa/lâu đài/tháp ngoài hành tinh), màn chọn bản đồ, quái theo vùng, 4 kiểu trùm, địa hình, xe, zombie, trùm + đá ném + cổng trạm, vật cản + thanh độ bền, bão, HUD (cả thanh máu trùm), menu, cửa hàng, gara, bảng tổng kết |
| Garage.kt | 5 dòng xe x 5 phiên bản (Vehicles), 10 vũ khí (WeaponType) + chỉ số theo cấp, giá +30% |
| Skills.kt | Cây kỹ năng (SkillStat, Perk, SkillNode, Skills) và cấp người chơi (Rank) |
| Story.kt | Cốt truyện: lời mở đầu, 4 chương theo vùng, kết thúc; trang đã mở |
| CarArt.kt | Vẽ 25 xe (có người lái xe máy, xích xe tăng) và 10 vũ khí, dùng cho trận đấu và gara |
| Entities.kt | Kiểu dữ liệu: State, Upgrade, ItemType, Zombie, Obstacle, Boss, BossRock, Debris, Projectile, Particle, FloatText, Car... |
| Rooms.kt | Chuỗi 30 phòng nhà kho: Power (15 sức mạnh), Foe, RoomBoss, RoomShot, Cash, Room (bục, quái, cửa), RoomRun (cấp, XP, sức mạnh, hàng chờ chọn), RoomRules (di chuyển, quái, trùm, vũ khí, sức mạnh, tiền) |
| Campaign.kt | v7: DefType (8 bệ), Blueprint/Blueprints (12 bản thiết kế), Def, Campaign (trại, ô, dựng/nâng/sửa/bán, đêm & đợt zombie, bắn, lục soát, lên đường) |
| CampDraw.kt | v7: vẽ trại ngày/đêm, tường 3 cấp, bệ, zombie tấn công, HUD trại, bảng dựng, xưởng (hàm mở rộng của Renderer) |
| tools/assets/tao_asset_colab.py | Script Google Colab tạo ảnh (Recraft + fal.ai xóa phông) và âm thanh (ElevenLabs) |
| Sprites.kt / SpriteDraw.kt | v7.1: nạp ảnh AI theo tên từ assets/img, vẽ sprite/hộp/nền; bảng tên cho zombie, bệ, sức mạnh, vũ khí phụ, xe, nền |
| tools/assets/nhap_asset.py | Nhập thư mục asset (từ Drive) vào app/src/main/assets, thu nhỏ ảnh |
| Missions.kt | Stat (bộ đếm trọn đời), 8 loại nhiệm vụ ngày x 3 mức, 16 thành tích, Missions.today()/forDay() |
| Interior.kt | Màn trong nhà: phòng, đồ đạc đứng lên được (Platform), rương (Chest), zombie trong nhà, đạn vũ khí phụ trong nhà |
| SideWeapons.kt | 7 vũ khí phụ của tài xế (SideWeapon): giá, ải mở, sát thương, tốc độ, tầm, cận chiến/bay cong |
| Sfx.kt | Hiệu ứng âm thanh tổng hợp ra WAV (cache theo phiên bản), phát bằng SoundPool khi đã nạp xong, có ưu tiên và giãn cách |
| Music.kt | 3 bài nhạc tổng hợp (chill 90 BPM La thứ, kịch tính 140 BPM Rê thứ, chiến thắng Đô trưởng), phát bằng AudioTrack có vòng lặp, chuyển bài mượt |
| SaveData.kt | SharedPreferences "car_survival_v3": tiền, ngày, ải đã vượt, ải chọn, kỷ lục từng ải, nâng cấp, xe/vũ khí sở hữu, âm thanh, nhạc, điểm cao nhất |

Hằng số quan trọng trong Game.kt: METER = 30 đơn vị/mét, G = 900, ZOOM = 0.8, STAGES = 20, LEVELS_PER_STAGE = 5, MAX_PER_HUNDRED = 20, PHASE_TIME = 60/20/5, STORM_SPEED = 150/450/1100/2600, SUBSTEP vật lý = 1/240 giây.

## 5. Cách lấy APK

- Cách 0 (dễ nhất, tải thẳng trên điện thoại, không cần đăng nhập): https://github.com/khongvinh/2d-game-car/releases/latest/download/DuaXeSinhTon.apk — mỗi lần push, workflow tự tạo/cập nhật Release tag v<versionName> kèm file DuaXeSinhTon.apk.
- Cách 1: GitHub → tab Actions → lần chạy "Build APK" mới nhất → tải artifact DuaXeSinhTon-apk → giải nén lấy app-debug.apk.
- Cách 2: build offline trong container (mục 6), file ra ở tools/dev/out/DuaXeSinhTon.apk.
- Mọi bản dùng cùng khóa ký nên cài đè được. Lưu ý: bản offline nhắm targetSdk 34, bản GitHub nhắm 35.

## 6. Môi trường và công cụ phát triển (đã lưu trong repo)

### Hạn chế của container
- dl.google.com và maven.google.com bị chặn nên không có Android SDK, không chạy được Gradle Android cục bộ.
- Maven Central đôi khi trả lỗi 429 (quá nhiều yêu cầu): thử lại sau vài giây, dùng repo1.maven.org.
- raw.githubusercontent.com và GitHub Releases tải được.

### Các bước dùng công cụ
```
bash tools/dev/setup.sh                  # tải android.jar (API 34), bundletool (d8 + apksig), Apktool (aapt2)
bash tools/dev/build_apk.sh 6 4.2        # build APK offline: versionCode 6, versionName 4.2
cd tools/dev/compile && gradle compileKotlin   # chỉ kiểm tra biên dịch
cd tools/dev/preview && gradle shots     # chụp ảnh màn hình + xuất nhạc WAV vào tools/dev/out/shots
cd tools/dev/preview && gradle sim       # mô phỏng vật lý trên các ải
cd tools/dev/preview && gradle ram       # thử tông xác xe 4 lần
cd tools/dev/preview && gradle boss      # thử đánh trùm ải 5 và ải 20
cd tools/dev/preview && gradle rooms     # bot qua 30 phòng ở ải 1/6/10 (-Pa=god: bất tử) + ảnh phòng, chọn sức mạnh, lái buôn, trùm
cd tools/dev/preview && gradle inside    # vào nhà ở 4 ải, lục rương, đánh zombie + ảnh màn âm thanh, tab vũ khí phụ
cd tools/dev/preview && gradle walk      # tài xế đứng yên khi bão đi qua (WALK=1: vừa đi vừa chạy bão)
```
- tools/dev/preview chạy chính mã game trên JVM với lớp giả lập android.graphics/media/content bằng Java2D (emoji hiện ô vuông, trên điện thoại hiện bình thường).
- Sign.java: đóng gói lại APK (resources.arsc không nén, căn lề 4 byte), thêm classes.dex, ký v2/v3 bằng app/debug.keystore.

### Quy trình mỗi lần sửa
- Sửa code → compileKotlin → (nếu đổi vật lý/hình ảnh) chạy preview để xem ảnh → tăng versionCode/versionName trong app/build.gradle.kts → build_apk.sh → commit → push lên claude/2d-driving-game-f0kpjk → kiểm tra GitHub Actions xanh → gửi APK cho người dùng.

## 7. Kết quả mô phỏng cân bằng (bot tự nghiêng xe khi bay)

| Cấu hình xe | Ải 1 | Ải 5 | Ải 10 | Ải 20 |
|---|---|---|---|---|
| Xe gốc chưa nâng cấp (40 giây ga) | ~750m | ~630m | ~370m | ~140m |
| Nâng cấp vừa (60 giây) | ~1660m | ~1120m | ~910m | ~550m |
| Nâng cấp tối đa + xe cơ bắp (80 giây) | — | ~2380m | ~1370m | ~1300m |
- Ván đầu thực tế ở ải 1 (có xăng giới hạn, zombie, vật cản) đi được khoảng 480m/700m: cần nâng cấp vài lần mới qua.
- Xe có thể lật/cắm đầu nếu không nghiêng đúng khi bay; lật quá 3 giây thì kết thúc ván.

## 8. Các quyết định thiết kế đã thống nhất
- "3 chặn" được hiểu là 3 zombie/vật cản mỗi 100m ở màn 1, +1 mỗi màn, giới hạn 20.
- Vòng bo là bức bão đuổi phía sau (phù hợp game cuộn ngang); ở các ải đầu bão chủ yếu bắt người bị kẹt/hết xăng, ở ải cao bão thành áp lực thời gian thật.
- Nhân bản = 1 lần hồi sinh trong 10 giây.
- Tiến độ lưu riêng ("car_survival_v3"): người chơi bản cũ bắt đầu lại.

## 9. Việc có thể làm tiếp / câu hỏi còn mở
- Người dùng chưa phản hồi về: độ khó đường mới, nhạc nền, cảm giác điều khiển trên điện thoại thật.
- Danh sách người dùng đã đồng ý làm dần: chỉnh độ khó sau khi chơi thử; bão gắt hơn từ đầu; thêm loại zombie mới (nhảy, mặc giáp); bản đồ mới (thành phố, rừng, tuyết); thêm xe/vũ khí (súng phun lửa, lưỡi cưa); chuẩn bị đưa lên Google Play (khóa ký release, icon, ảnh giới thiệu).
- Đã xong từ danh sách này: trùm cuối mỗi 5 ải (v4.2); bản đồ mới theo vùng + quái theo vùng (v4.3).
- v4.3: người dùng xác nhận nhạc đã khớp.
- Chờ người dùng chơi thử v4.5: kiểu đường mới, giao diện thép gỉ, còn kẹt xe không; v4.4 (cày tiền, giá, cảm giác lái) chưa có phản hồi cụ thể.
- CH Play / AAB (người dùng hỏi, bảo để sau): code đạt (64-bit, không quyền, không thu dữ liệu). Còn thiếu: khóa ký upload riêng (đang ký debug, mật khẩu "android" công khai — Play từ chối), build bundleRelease (.aab) trên CI với khóa trong GitHub Secrets, kiểm tra targetSdk (có thể đã phải 36 từ 31/8/2026), icon 512, ảnh bìa 1024x500, ảnh chụp, trang chính sách quyền riêng tư, mô tả (tránh nhắc "Earn to Die"); phía người dùng: tài khoản nhà phát triển 25 USD + xác minh danh tính, thử nghiệm kín 12 người x 14 ngày (tài khoản cá nhân mới), bảng xếp hạng độ tuổi (bạo lực zombie → khoảng 12+/Teen).
- Chưa tạo Pull Request; khi người dùng muốn gộp vào nhánh chính thì tạo PR từ claude/2d-driving-game-f0kpjk.

## 10. Các phiên chat

| Phiên | Tiêu đề | Phạm vi |
|---|---|---|
| #2 | Đua Xe Sinh Tồn #2 — v5.0→v7.2: lá chắn đi bộ, âm lượng, nhà kho 30 phòng kiểu Earn to Die Rogue, menu bản đồ, nhiệm vụ, LÀM LẠI thành thủ trại (ngày lục soát/lên đường, đêm thủ thành), script tạo asset AI | v5.1→v7.2 + bài thử preview `inside`, `walk`, `rooms`, `camp`; script asset Colab miễn phí; nhập ảnh AI |
| #1 | Đua Xe Sinh Tồn #1 — v1.0→v5.0: tạo game, kiểu Earn to Die, bão 3 bo, nhạc, trùm cuối, 20 bản đồ, gara 25 xe, 20 kiểu đường, giao diện thép, tiếng động cơ, ngọc đỏ, lô cốt, cốt truyện, cây kỹ năng, tài xế lục soát, xe nổ tài xế chạy tiếp, nhảy, đổi vị trí phím | Từ đầu đến v5.0 + tài liệu bàn giao, công cụ tools/dev, quy tắc CLAUDE.md |

- Quy tắc làm việc (đặt tiêu đề phiên, tự lưu tiến trình khi ngữ cảnh ≥ 90% rồi nhắc mở chat mới) nằm trong CLAUDE.md ở gốc repo.

## 11. Hướng dẫn cho phiên chat mới
- Mở phiên mới với repo khongvinh/2d-game-car, nhánh claude/2d-driving-game-f0kpjk.
- Câu mở đầu gợi ý: "Đọc docs/TIEN_TRINH_DU_AN.md trong repo khongvinh/2d-game-car (nhánh claude/2d-driving-game-f0kpjk) rồi làm tiếp: ..."
- Trợ lý nên: đọc tài liệu này, chạy tools/dev/setup.sh, kiểm tra build, rồi mới sửa code; mỗi bản mới tăng versionCode và gửi APK.
- Sau khi làm xong mỗi đợt việc, cập nhật lại tài liệu này (và tạo lại bản Word bằng: node tools/dev/md2docx.js docs/TIEN_TRINH_DU_AN.md docs/TIEN_TRINH_DU_AN.docx).
