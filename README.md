# Đua Xe Sinh Tồn 🚗💨

Game lái xe 2D cho Android (Kotlin + Canvas, không dùng engine hay thư viện ngoài).

## Luật chơi

- Xe tự tăng tốc; **kéo ngón tay sang trái/phải** để lái.
- Mỗi màn có **giới hạn thời gian** để về **ĐÍCH**.
- Khi đã trôi qua ~55% thời gian, **vòng bo** (vùng tím) bắt đầu thu hẹp dần về phía mặt đường.
  **Hết giờ**, vòng bo khép lại hoàn toàn trong 4 giây và **tiêu diệt bạn** nếu chưa về đích.
- Va chạm làm **mất máu** và **giảm tốc độ** (mất thời gian!). Hết máu là thua.

### Chướng ngại vật
| Vật cản | Sát thương | Bắn phá được |
|---|---|---|
| Xe con | 25 | ✔ (2 phát) |
| Xe tải (từ màn 2) | 35 | ✔ (4 phát) |
| Thùng phuy | 15 | ✔ |
| Nón giao thông | 8 | ✔ |
| Đá tảng | 30 | ✘ |
| Vết dầu | 0 – làm xe trượt | ✘ |

### Vật phẩm
| Biểu tượng | Tác dụng |
|---|---|
| ✚ Máu | +30 máu |
| $ Tiền | +tiền (dùng để nâng cấp) |
| 🛡 Lá chắn | Bất tử trong 6 giây, đâm nát vật cản |
| 🚀 Vũ khí | Tự động bắn 2 nòng trong 7 giây, phá vật cản để nhận tiền |
| ⏱ Đồng hồ | +5 giây |

Về đích sớm được **thưởng tiền** theo thời gian còn lại. Màn sau nhanh hơn, dài hơn và nhiều vật cản hơn.

### Nâng cấp (dùng tiền)
Máu tối đa · Giáp xe · Vũ khí · Lá chắn — mỗi loại tối đa 5 cấp. Tiền và nâng cấp được lưu lại.

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
├── Entities.kt       # Kiểu dữ liệu: xe, vật cản, vật phẩm, đạn, hạt hiệu ứng
└── SaveData.kt       # Lưu tiền, kỷ lục, cấp nâng cấp
```
