---
name: lam-tiep
description: Tiếp tục làm game "Đua Xe Sinh Tồn" ở phiên chat mới — đọc tài liệu tiến trình, chuẩn bị môi trường, rồi làm việc người dùng ghi sau lệnh. Dùng khi người dùng gõ /lam-tiep hoặc nói "làm tiếp game", "đọc tiến trình rồi làm tiếp".
---

# Làm tiếp game Đua Xe Sinh Tồn

Người dùng gọi: `/lam-tiep <việc cần làm>` (có thể bỏ trống phần việc).

## Các bước bắt buộc
1. Đọc `docs/TIEN_TRINH_DU_AN.md` (đọc mục phiên bản mới nhất trước — từ v7.0 game là kiểu THỦ TRẠI; các mục cũ là lịch sử) và `CLAUDE.md`.
2. Làm trên nhánh `claude/2d-driving-game-f0kpjk`: `git fetch origin claude/2d-driving-game-f0kpjk && git checkout claude/2d-driving-game-f0kpjk && git pull`.
3. Chạy nền `bash tools/dev/setup.sh` (container không có Android SDK).
4. Đặt tiêu đề phiên (tool `set_session_title`) theo mẫu trong CLAUDE.md, số phiên tiếp nối mục "Các phiên chat" của tài liệu.
5. Nếu người dùng ghi việc sau lệnh → làm việc đó. Nếu bỏ trống → tóm tắt ngắn trạng thái hiện tại + mục "Việc tiếp" của phiên bản mới nhất và hỏi muốn làm gì.

## Khi sửa game (theo CLAUDE.md)
Sửa code → `cd tools/dev/compile && gradle compileKotlin` (hoặc `cd tools/dev/preview && gradle camp` để chạy thử + chụp ảnh trại/đêm/lục soát/xưởng) → tăng versionCode/versionName trong `app/build.gradle.kts` và `Game.VERSION` → `bash tools/dev/build_apk.sh <code> <name>` → commit, push → kiểm tra GitHub Actions "Build APK" xanh → gửi APK + ảnh chụp cho người dùng → cập nhật tài liệu + bản Word (`node tools/dev/md2docx.js docs/TIEN_TRINH_DU_AN.md docs/TIEN_TRINH_DU_AN.docx`).

## Asset AI (ảnh/âm thanh)
- Người dùng tạo bằng Colab (`tools/assets/tao_asset_colab_gpu.py`), lưu Drive thư mục `14i1_kbJc9H1rZfqnH9T_pj3xFs0gynEw` → `DuaXeSinhTon_assets/`.
- Zip > 10MB không tải được bằng connector: tìm từng file (search_files theo mimeType + modifiedTime) rồi download_file_content từng id; giao việc tải hàng loạt cho subagent để đỡ tốn ngữ cảnh. KHÔNG mở bảng "Key API".
- Nhập: `python3 tools/assets/nhap_asset.py <thư mục>` (chia nhóm nen/nhac/am_thanh/khac). Game nạp theo tên (Sprites.kt).

- Tự đề xuất vẽ AI cho mọi thứ còn vẽ code / ảnh AI lỗi (xem CLAUDE.md) — không đợi người dùng nhắc.

## Trả lời
Luôn bằng tiếng Việt. Theo dõi ngữ cảnh: ≥ 90% thì lưu tiến trình và nhắc mở chat mới (xem CLAUDE.md).
