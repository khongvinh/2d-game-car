# Đua Xe Sinh Tồn 🚗💨

Game lái xe 2D cho Android (Kotlin + Canvas, không dùng engine hay thư viện ngoài).

## Luật chơi

- Xe tự tăng tốc; **kéo ngón tay sang trái/phải** để lái. Về **ĐÍCH** trước khi vòng bo khép lại.
- **Vòng bo** (vùng tím) có 3 giai đoạn, mỗi màn đếm lại từ đầu:
  | Giai đoạn | Thời gian | Vòng bo | Sát thương ngoài bo |
  |---|---|---|---|
  | Bo 1 | 60 giây | Thu dần từ toàn màn hình về mép đường | 15/giây |
  | Bo 2 | 20 giây | Thu về 2 làn giữa (2 làn ngoài nguy hiểm) | 25/giây |
  | Bo 3 | 5 giây (đếm ngược 5-4-3-2-1) | Khép hoàn toàn | 45/giây |
  | Hết bo | — | Toàn bộ là vùng chết | 150/giây |
- Va chạm làm **mất máu** và **giảm tốc độ**. Hết máu là thua.

### Độ khó
- **Màn 1: mỗi đợt 3 chướng ngại vật, mỗi màn sau +1** (cấp số cộng: 3, 4, 5, …).
- Từ màn 7 có thể chặn 2 làn cùng lúc, từ màn 13 chặn 3 làn (luôn chừa 1 làn trống).
- Tốc độ xe và chiều dài đường tăng dần theo màn.

### Ải
- **5 màn = 1 ải, tổng cộng 20 ải** (100 màn). Vượt ải được thưởng `100 × số ải` tiền.
- Ở menu có thể chọn bắt đầu từ bất kỳ ải nào đã mở.
- **Qua ải 1** → mở **GARA**: đổi xe và vũ khí.
- **Qua ải 5** → mở vật phẩm **👥 Nhân bản**: thêm một xe sao chép chạy song song trong 10 giây — tính là thêm 1 mạng (đỡ một cú va chạm), cũng bắn và nhặt đồ.

### Chướng ngại vật
| Vật cản | Sát thương | Bắn phá được |
|---|---|---|
| Xe con | 25 | ✔ |
| Xe tải (từ màn 3) | 35 | ✔ |
| Thùng phuy | 15 | ✔ |
| Nón giao thông | 8 | ✔ |
| Đá tảng | 30 | chỉ Máy ném đá |
| Vết dầu | 0 – làm xe trượt | ✘ |

### Vật phẩm
| Biểu tượng | Tác dụng |
|---|---|
| ✚ Máu | +30 máu |
| $ Tiền | +tiền |
| 🛡 Lá chắn | Bất tử 6 giây, đâm nát vật cản |
| 🚀 Vũ khí | Tự bắn bằng vũ khí đang chọn trong 7 giây |
| ⏱ Đồng hồ | +5 giây cho giai đoạn bo hiện tại |
| 👥 Nhân bản | x2 mạng trong 10 giây (mở sau ải 5) |

### Gara (mở sau ải 1)
| Xe | Máu | Tốc độ | Đặc điểm | Giá |
|---|---|---|---|---|
| Xe đua | x1.0 | x1.0 | Cân bằng | có sẵn |
| Bán tải | x1.4 | x0.96 | Giáp 5% | 800 |
| Siêu xe | x0.85 | x1.12 | Lái rất nhạy | 1500 |
| Bọc thép | x1.8 | x0.92 | Giáp 20%, lái nặng | 3000 |

| Vũ khí | Đặc điểm | Giá |
|---|---|---|
| 🔫 Súng máy | 2 nòng, bắn nhanh | có sẵn |
| 🏹 Nỏ | Tên sát thương 3, xuyên 3 mục tiêu | 1000 |
| ☄ Máy ném đá | Đá bay vòng cung, nổ diện rộng, phá được cả đá tảng | 2000 |

### Nâng cấp
Máu tối đa · Giáp xe · Vũ khí · Lá chắn — mỗi loại tối đa 5 cấp.

### Âm thanh
Toàn bộ hiệu ứng âm thanh (tiền, va chạm, nổ, súng/nỏ/máy ném đá, bíp đếm ngược, vòng bo, thắng/thua…)
được tổng hợp bằng code khi mở game, không cần file âm thanh. Bật/tắt bằng nút 🔊 ở menu.

## Cài đặt trên điện thoại

1. Vào tab **Actions** của repo → chọn lần chạy **Build APK** mới nhất → tải artifact **DuaXeSinhTon-apk**.
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
├── MainActivity.kt   # Activity toàn màn hình
├── GameView.kt       # SurfaceView + vòng lặp game, cảm ứng
├── Game.kt           # Luật chơi: di chuyển, va chạm, vòng bo, sinh vật cản/vật phẩm
├── Renderer.kt       # Vẽ đường, xe, vật phẩm, vòng bo, HUD, menu
├── Entities.kt       # Kiểu dữ liệu: xe, vũ khí, vật cản, vật phẩm, đạn, hạt hiệu ứng
├── Sfx.kt            # Hiệu ứng âm thanh tổng hợp (SoundPool)
└── SaveData.kt       # Lưu tiền, ải đã vượt, gara, nâng cấp
```
