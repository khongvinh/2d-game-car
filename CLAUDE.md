# Hướng dẫn làm việc cho dự án "Đua Xe Sinh Tồn"

Người dùng giao tiếp bằng tiếng Việt; trả lời bằng tiếng Việt.

## Bắt đầu mỗi phiên
1. Đọc `docs/TIEN_TRINH_DU_AN.md` (tổng hợp toàn bộ tiến trình, luật chơi, số liệu, công cụ).
2. Làm việc trên nhánh `claude/2d-driving-game-f0kpjk`.
3. Chạy `bash tools/dev/setup.sh` trước khi build/kiểm tra (container không có Android SDK).

## Đặt tiêu đề phiên chat
Ngay đầu phiên và mỗi khi xong một đợt việc, đặt tiêu đề phiên (tool `set_session_title`, bỏ trống
session_id = phiên hiện tại) theo mẫu:

`Đua Xe Sinh Tồn #<số thứ tự phiên> — v<bản đầu>→v<bản cuối>: <các việc chính đã làm>`

Số thứ tự phiên tiếp nối mục "Các phiên chat" trong `docs/TIEN_TRINH_DU_AN.md`
(phiên #1 = v1.0→v4.1).

## Tự động lưu tiến trình khi gần hết ngữ cảnh (bắt buộc)
- Sau mỗi yêu cầu của người dùng (khi đã xong việc), gọi tool `get_session` (bỏ trống session_id) và đọc
  `external_metadata.context_usage` (used_tokens / max_tokens).
- Khi mức dùng **≥ 90%** (hoặc ≥ 85% nếu việc tiếp theo có vẻ lớn):
  1. Cập nhật `docs/TIEN_TRINH_DU_AN.md`: lịch sử phiên bản, luật chơi/số liệu đã đổi, việc còn dở,
     và thêm dòng cho phiên hiện tại ở mục "Các phiên chat".
  2. Tạo lại bản Word: `node tools/dev/md2docx.js docs/TIEN_TRINH_DU_AN.md docs/TIEN_TRINH_DU_AN.docx`.
  3. Commit + push lên nhánh làm việc, gửi file .docx cho người dùng.
  4. Đặt tiêu đề phiên theo mẫu ở trên.
  5. Nhắc người dùng mở đoạn chat mới, kèm câu mở đầu:
     "Đọc docs/TIEN_TRINH_DU_AN.md trong repo khongvinh/2d-game-car (nhánh claude/2d-driving-game-f0kpjk) rồi làm tiếp: …"
- Ngoài ra, mỗi khi xong một phiên bản mới cũng cập nhật tài liệu này để nó luôn khớp với code.

## Quy trình mỗi lần sửa game
Sửa code → `cd tools/dev/compile && gradle compileKotlin` → (đổi hình/vật lý thì chạy
`cd tools/dev/preview && gradle shots` và xem ảnh) → tăng versionCode/versionName trong
`app/build.gradle.kts` → `bash tools/dev/build_apk.sh <code> <name>` → commit, push → kiểm tra
GitHub Actions "Build APK" xanh → gửi APK cho người dùng.
