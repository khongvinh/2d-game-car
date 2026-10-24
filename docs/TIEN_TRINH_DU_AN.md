# TỔNG HỢP TIẾN TRÌNH DỰ ÁN "ĐUA XE SINH TỒN"

Tài liệu bàn giao để tiếp tục làm việc ở phiên chat mới mà không bị thiếu sót. Cập nhật đến phiên bản v4.3 (ngày 24/10/2026).

## 1. Thông tin chung

| Mục | Giá trị |
|---|---|
| Tên game | Đua Xe Sinh Tồn (package com.khongvinh.carsurvival) |
| Thể loại | Lái xe sinh tồn 2D cuộn ngang, phong cách Earn to Die |
| Nền tảng | Android 7.0+ (minSdk 24), màn hình ngang |
| Ngôn ngữ / công nghệ | Kotlin + Canvas (SurfaceView), không engine, không thư viện ngoài, không file ảnh/âm thanh |
| Repo GitHub | khongvinh/2d-game-car |
| Nhánh làm việc | claude/2d-driving-game-f0kpjk (chưa có Pull Request, chưa merge) |
| Commit mới nhất | xem git log nhánh làm việc (v4.3: 20 bản đồ, quái theo vùng, 4 trùm, sửa âm thanh) |
| Phiên bản hiện tại | v4.3 (versionCode 7) |
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

### v4.3 — Sửa âm thanh trễ, chọn bản đồ, 20 bản đồ, quái vật theo vùng, 4 trùm mới (hiện tại)
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

## 3. Luật chơi hiện tại (v4.3)

### Điều khiển
| Nút | Tác dụng |
|---|---|
| GA (góc phải dưới, hoặc chạm nửa phải màn hình) | Tăng tốc, tốn xăng |
| PHANH (trái) | Phanh / lùi |
| ↺ ↻ (trái dưới) | Nghiêng xe ra sau / ra trước (mạnh hơn khi đang bay) |
| 🚀 | Tên lửa đẩy, có bình riêng |
| Vũ khí trên nóc | Tự bắn zombie/vật cản phía trước khi còn đạn |
| II (phải trên) | Tạm dừng; nút Back cũng tạm dừng |

### Vòng chơi
- Mỗi lần chạy là một ngày. Ván kết thúc khi: hết xăng và đứng yên 2 giây, xe bị phá hủy (nổ), lật/kẹt quá 3 giây, rơi khỏi đường, bị bão nuốt, hoặc bỏ cuộc.
- Tiền nhận: 0,4$ mỗi mét + tiền thưởng (zombie, vật cản, tiền nhặt, qua mốc màn, bay xa trên 1,2 giây). Tới trạm cứu hộ thêm 200$ x số ải.
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

### Nâng cấp (mỗi loại 5 cấp, giá = base x (cấp+1) + 50 x cấp²)
| Nâng cấp | Base | Tác dụng |
|---|---|---|
| Động cơ | 150 | Tốc độ tối đa 700 + 110/cấp; lực kéo 520 + 80/cấp |
| Bình xăng | 120 | 22 + 6 giây ga/cấp |
| Tên lửa đẩy | 180 | 1,5 + 1,2 giây/cấp |
| Giáp & máu | 140 | Máu 100 + 30/cấp; giáp +6%/cấp (tối đa 70%) |
| Đạn dược | 100 | 20 + 20 viên/cấp |
| Lốp xe | 110 | Độ bám 1,0 + 0,15/cấp |

### Gara (mở sau ải 1)
| Xe | Máu | Tốc độ | Lực kéo | Giáp | Xăng | Giá |
|---|---|---|---|---|---|---|
| Bán tải cũ | x1.0 | x1.0 | x1.0 | 0% | x1.0 | có sẵn |
| Xe cơ bắp | x0.9 | x1.18 | x1.15 | 0% | x0.95 | 1500 |
| Xe tải thùng | x1.5 | x0.95 | x1.1 | 10% | x1.4 | 3000 |
| Xe bọc thép | x2.0 | x1.05 | x1.35 | 30% | x1.2 | 6000 |

| Vũ khí | Đặc điểm | Giá |
|---|---|---|
| Súng máy | Bắn nhanh (0,16 giây), sát thương 1 | có sẵn |
| Nỏ | 0,45 giây, sát thương 3, xuyên 3 mục tiêu | 1200 |
| Máy ném đá | 0,8 giây, đá bay vòng cung, nổ bán kính 160 | 2500 |

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
| Terrain.kt | Sinh địa hình: đồi nhiều tầng, đá, ổ gà, ván giặt, dốc nhảy, giới hạn dốc 38°, bo thung lũng, cảnh vật |
| Themes.kt | 4 vùng (Region) và 20 bản đồ (MapTheme): tên, màu trời/núi/đất/cỏ, kiểu đường chân trời |
| Renderer.kt | Vẽ toàn bộ: nền theo bản đồ (trời/núi/thành phố/núi lửa/lâu đài/tháp ngoài hành tinh), màn chọn bản đồ, quái theo vùng, 4 kiểu trùm, địa hình, xe, zombie, trùm + đá ném + cổng trạm, vật cản + thanh độ bền, bão, HUD (cả thanh máu trùm), menu, cửa hàng, gara, bảng tổng kết |
| Entities.kt | Kiểu dữ liệu: State, CarType, WeaponType, Upgrade, ItemType, Zombie, Obstacle, Boss, BossRock, Debris, Projectile, Particle, FloatText, Car... |
| Sfx.kt | Hiệu ứng âm thanh tổng hợp ra WAV (cache theo phiên bản), phát bằng SoundPool khi đã nạp xong, có ưu tiên và giãn cách |
| Music.kt | 3 bài nhạc tổng hợp (chill 90 BPM La thứ, kịch tính 140 BPM Rê thứ, chiến thắng Đô trưởng), phát bằng AudioTrack có vòng lặp, chuyển bài mượt |
| SaveData.kt | SharedPreferences "car_survival_v3": tiền, ngày, ải đã vượt, ải chọn, kỷ lục từng ải, nâng cấp, xe/vũ khí sở hữu, âm thanh, nhạc, điểm cao nhất |

Hằng số quan trọng trong Game.kt: METER = 30 đơn vị/mét, G = 900, ZOOM = 0.8, STAGES = 20, LEVELS_PER_STAGE = 5, MAX_PER_HUNDRED = 20, PHASE_TIME = 60/20/5, STORM_SPEED = 150/450/1100/2600, SUBSTEP vật lý = 1/240 giây.

## 5. Cách lấy APK

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
- Chờ người dùng chơi thử v4.3: âm thanh còn trễ không, độ khó 4 trùm, màu các map.
- Chưa tạo Pull Request; khi người dùng muốn gộp vào nhánh chính thì tạo PR từ claude/2d-driving-game-f0kpjk.

## 10. Các phiên chat

| Phiên | Tiêu đề | Phạm vi |
|---|---|---|
| #1 | Đua Xe Sinh Tồn #1 — v1.0→v4.3: tạo game, kiểu Earn to Die, bão 3 bo, nhạc, độ bền vật cản, trùm cuối, 20 bản đồ, 4 trùm | Từ đầu đến v4.3 + tài liệu bàn giao, công cụ tools/dev, quy tắc CLAUDE.md |

- Quy tắc làm việc (đặt tiêu đề phiên, tự lưu tiến trình khi ngữ cảnh ≥ 90% rồi nhắc mở chat mới) nằm trong CLAUDE.md ở gốc repo.

## 11. Hướng dẫn cho phiên chat mới
- Mở phiên mới với repo khongvinh/2d-game-car, nhánh claude/2d-driving-game-f0kpjk.
- Câu mở đầu gợi ý: "Đọc docs/TIEN_TRINH_DU_AN.md trong repo khongvinh/2d-game-car (nhánh claude/2d-driving-game-f0kpjk) rồi làm tiếp: ..."
- Trợ lý nên: đọc tài liệu này, chạy tools/dev/setup.sh, kiểm tra build, rồi mới sửa code; mỗi bản mới tăng versionCode và gửi APK.
- Sau khi làm xong mỗi đợt việc, cập nhật lại tài liệu này (và tạo lại bản Word bằng: node tools/dev/md2docx.js docs/TIEN_TRINH_DU_AN.md docs/TIEN_TRINH_DU_AN.docx).
