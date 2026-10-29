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

### Cốt truyện
Năm 2049, mưa thiên thạch đỏ biến loài người thành xác sống. Bạn — người thợ máy cuối cùng — lái chiến xa chạy trước cơn bão tím, tìm Trạm Cứu Hộ, hạ 4 trùm và phá tàu mẹ ngoài hành tinh. Đọc lại ở nút 📖 CỐT TRUYỆN.

### Xuống xe lục soát
Dừng xe cạnh một ngôi nhà → **XUỐNG XE**, đi bộ tới cửa, **LỤC SOÁT** lấy tiền, ngọc, xăng, máu và mảnh xe. Tài xế tự bắn quái bằng súng ngắn; coi chừng bị cắn và cơn bão!

### Lô cốt
Lô cốt bê tông dọc đường tự xoay súng bắn vào xe. Phá bằng vũ khí hoặc tông có đà để lấy tiền và ngọc.

### Bản đồ & kiểu đường
Mỗi ải một kiểu đường: đồng bằng, đồi cát, đèo đá, xa lộ có hầm chui, hang động có trần, cầu qua vực / dung nham / đầm lầy / hư không.
Màn hình chính có **bảng xếp hạng** 5 lượt chạy xa nhất.

Nút **🗺 ẢI** ở menu mở màn chọn bản đồ: 20 bản đồ trong 4 vùng — Sa mạc (ải 1–5), Núi lửa (6–10),
Rừng đêm (11–15), Hành tinh lạ (16–20). Mỗi vùng có cảnh và quái riêng: zombie, thằn lằn lửa, dơi,
người ngoài hành tinh và khối nhớt.

### Trùm cuối
Cuối các ải 5, 10, 15, 20 có trùm chắn trước trạm cứu hộ: Gã Khổng Lồ (zombie), Rồng Lửa, Dơi Hút Máu,
Quái Vật Ngoài Hành Tinh. Cổng trạm chỉ mở khi trùm gục.

### Vật phẩm trên đường
`$` tiền · `⛽` xăng · `✚` sửa xe · `🛡` lá chắn 6 giây · `🎯` +15 đạn · `⏱` +5 giây & đẩy bão lùi · `👥` nhân bản

### Gara (xưởng gạch, nút tròn: Nhà · Xe · Vũ khí · Kỹ năng)
**5 dòng xe, mỗi dòng 5 phiên bản** (25 xe): Xe máy (có sẵn) → Xe hơi (qua ải 5) → Bán tải (qua ải 10)
→ Bọc thép (qua ải 15) → Xe tăng (qua ải 20). Phiên bản sau khỏe hơn (máu, động cơ, xăng, tên lửa, đạn đều tăng theo xe), giá cao hơn (780$ → 58.500$). Xe mới cần **5 mảnh** tìm được khi **lục soát nhà** rồi trả tiền lắp ráp. **Cây kỹ năng**: 21 nút chỉ số + 6 kỹ năng đặc biệt, mở bằng **ngọc đỏ ♦** (mỗi nút sau đắt hơn 20%). Ngôi sao cấp người chơi tăng theo XP.

**10 vũ khí, mỗi loại nâng 5 cấp:** Súng lục, Dao chém (không tốn đạn), Súng máy, Nỏ, Súng hoa cải,
Máy ném đá, Súng phun lửa, Tên lửa tự dẫn, Súng laser, Súng nòng xoay. Mỗi vũ khí có sát thương,
tốc bắn, tầm bắn, xuyên giáp và bán kính nổ riêng.

Tiền đến chủ yếu từ việc hạ quái và phá vật cản; ải càng cao quái càng nhiều tiền — phải cày!

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

**Cách nhanh (trên điện thoại):** mở https://github.com/khongvinh/2d-game-car/releases/latest/download/DuaXeSinhTon.apk
để tải thẳng file APK mới nhất (hoặc vào mục **Releases** của repo).

**Cách khác:**
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
