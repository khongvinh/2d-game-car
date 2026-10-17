# Đua Xe Sinh Tồn 🚗🧟

Game lái xe sinh tồn 2D cho Android theo phong cách *Earn to Die*: màn hình ngang, cuộn ngang,
xe có vật lý (nhún, bay, lật), lao qua đồi sa mạc đầy zombie, thùng gỗ, thùng phuy, xác xe —
và chạy trước **cơn bão (vòng bo)** đuổi phía sau. Viết bằng Kotlin + Canvas, không dùng engine
hay thư viện ngoài, không cần file ảnh/âm thanh.

## Cách chơi

| Nút | Tác dụng |
|---|---|
| **GA** (phải, hoặc chạm nửa phải màn hình) | Tăng tốc (tốn xăng) |
| **PHANH** | Phanh / lùi |
| **↺ ↻** | Nghiêng xe ra sau / ra trước (dùng khi bay để tiếp đất bằng bánh) |
| **🚀** | Tên lửa đẩy (có bình riêng) |
| Súng trên nóc | Tự động bắn zombie và vật cản phía trước (hết đạn thì thôi) |

- Mỗi lần chạy là **một ngày**. Ván kết thúc khi **hết xăng**, **xe bị phá hủy**, **lật xe**
  hoặc **bị bão nuốt**. Bạn nhận tiền theo quãng đường, zombie tông được, vật cản phá được,
  tiền nhặt được — rồi **nâng cấp** và chạy lại để đi xa hơn, cho tới khi về **TRẠM CỨU HỘ**.
- Tông zombie ở tốc độ cao thì chúng văng đi; đi chậm thì chúng bám vào cào xe.
- Thùng gỗ cần đủ tốc độ mới phá được, xác xe cần rất nhanh (hoặc tên lửa), thùng phuy phát nổ.
- Không đủ tốc độ để phá ngay? Vật cản hiện **thanh độ bền**: lùi lại lấy đà và tông tiếp —
  mỗi cú tông trừ **25%**, 4 cú là vật cản vỡ tung. Đạn cũng làm giảm thanh này.

### Vòng bo (cơn bão tím)
| Giai đoạn | Thời gian | Tốc độ bão |
|---|---|---|
| Bo 1 | 60 giây | Chậm — chỉ nuốt xe bị kẹt/hết xăng |
| Bo 2 | 20 giây | Nhanh |
| Bo 3 | 5 giây (đếm ngược 5-4-3-2-1) | Rất nhanh |
| Hết bo | — | Gần như tức thì: ai chưa về đích sẽ bị tiêu diệt |

### Ải và màn
- **20 ải**, mỗi ải là một con đường dài hơn ải trước (700m → 2600m), chia làm **5 màn** (cột mốc).
  Qua mỗi mốc được thưởng tiền.
- **Độ khó theo cấp số cộng:** màn 1 có **3 zombie/vật cản mỗi 100m**, mỗi màn sau **+1**
  (tối đa 20). Địa hình cũng dốc hơn, nhiều dốc nhảy hơn; zombie béo xuất hiện từ ải 2, xác xe từ ải 2.
- **Qua ải 1** → mở **GARA**: đổi xe và vũ khí.
- **Qua ải 5** → mở vật phẩm **👥 Nhân bản**: trong 10 giây bạn có thêm 1 mạng
  (bị phá hủy, lật xe hay dính bão sẽ hồi sinh ngay).

### Vật phẩm trên đường
`$` tiền · `⛽` xăng · `✚` sửa xe · `🛡` lá chắn 6 giây · `🎯` +15 đạn · `⏱` +5 giây & đẩy bão lùi · `👥` nhân bản

### Nâng cấp
Động cơ · Bình xăng · Tên lửa đẩy · Giáp & máu · Đạn dược · Lốp xe — mỗi loại 5 cấp.

### Gara
| Xe | Máu | Tốc độ | Giáp | Giá |
|---|---|---|---|---|
| Bán tải cũ | x1.0 | x1.0 | 0% | có sẵn |
| Xe cơ bắp | x0.9 | x1.18 | 0% | 1500 |
| Xe tải thùng | x1.5 | x0.95 | 10%, bình xăng lớn | 3000 |
| Xe bọc thép | x2.0 | x1.05 | 30%, động cơ khỏe | 6000 |

| Vũ khí | Đặc điểm | Giá |
|---|---|---|
| 🔫 Súng máy | Bắn nhanh | có sẵn |
| 🏹 Nỏ | Sát thương 3, xuyên 3 mục tiêu | 1200 |
| ☄ Máy ném đá | Đá bay vòng cung, nổ diện rộng | 2500 |

### Địa hình
Đồi dốc nhiều tầng, đá nhô, ổ gà, đoạn "ván giặt" rung lắc và dốc nhảy — càng lên ải cao càng
gồ ghề. Mặt dốc lên tối đa ~38° để luôn leo được nếu đủ đà/động cơ.

### Hiệu ứng
- Mỗi lần va chạm hiện số máu bị trừ bay lên và viền màn hình chớp đỏ.
- Máu dưới 15%: xe bốc cháy, khói đen cuồn cuộn.
- Xe bị phá hủy (hoặc bị bão nuốt): nổ tung, mảnh vỡ văng ra, xác xe cháy đen.
- Bảng tổng kết chỉ hiện **điểm số**, thống kê và **🏆 thành tích** của ngày chạy
  (kỷ lục, sát thủ zombie, xe ủi, bay cao, tốc độ bàn thờ, thoát bão trong gang tấc…).

### Âm thanh & nhạc nền
- Hiệu ứng âm thanh (tông zombie, gỗ vỡ, nổ, súng/nỏ/máy ném đá, tên lửa, tiếp đất, bíp đếm ngược,
  bão, thắng/thua…) được tổng hợp bằng code. Bật/tắt bằng nút 🔊 ở menu.
- Nhạc nền cũng được soạn và tổng hợp bằng code: **chill** ở menu và đầu ván, chuyển sang
  **kịch tính** khi qua nửa đường / bão tăng tốc / máu thấp, và **nhạc chiến thắng** tung hô khi tới
  trạm cứu hộ. Bật/tắt bằng nút 🎵 ở menu.

## Cài đặt trên điện thoại

1. Vào tab **Actions** của repo → lần chạy **Build APK** mới nhất → tải artifact **DuaXeSinhTon-apk**.
2. Giải nén lấy `app-debug.apk`, chép vào điện thoại và mở để cài (cho phép "Cài ứng dụng không rõ nguồn gốc").

Yêu cầu Android 7.0 (API 24) trở lên.

## Tự build

Cần JDK 17 và Android SDK (API 35):

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Cấu trúc mã nguồn

```
app/src/main/java/com/khongvinh/carsurvival/
├── MainActivity.kt   # Activity toàn màn hình, nằm ngang
├── GameView.kt       # SurfaceView + vòng lặp game, cảm ứng đa điểm
├── Game.kt           # Luật chơi: ải/màn, zombie, vật cản, vũ khí, vật phẩm, bão, nâng cấp
├── CarPhysics.kt     # Vật lý xe: nhún lò xo, bám đường, nghiêng, tên lửa, va chạm thân xe
├── Terrain.kt        # Sinh địa hình đồi dốc, dốc nhảy, cảnh vật
├── Renderer.kt       # Vẽ bầu trời, núi, địa hình, xe, zombie, bão, HUD, menu, gara
├── Entities.kt       # Kiểu dữ liệu: xe, vũ khí, nâng cấp, zombie, vật cản, vật phẩm, hiệu ứng
├── Sfx.kt            # Hiệu ứng âm thanh tổng hợp (SoundPool)
├── Music.kt          # Nhạc nền tổng hợp: chill, kịch tính, chiến thắng (AudioTrack)
└── SaveData.kt       # Lưu tiền, ngày, ải đã vượt, kỷ lục, gara, nâng cấp
```
