---
name: nhap-anh
description: Tải ảnh AI vừa render trên Colab (Google Drive) về, kiểm tra từng ảnh, cài vào game "Đua Xe Sinh Tồn", build và gửi APK. Dùng khi người dùng gõ /nhap-anh hoặc nói "đã chạy xong script / đợt N", "render xong rồi", "tải ảnh về cài vào game".
---

# Nhập ảnh AI vừa render vào game

Người dùng gọi: `/nhap-anh [ghi chú]` sau khi chạy `tools/assets/tao_anh_colab.py` (và/hoặc `tao_khung_hinh_colab.py`) trên Colab.

## 0. Chuẩn bị
- Làm theo CLAUDE.md (nhánh, `bash tools/dev/setup.sh` chạy nền nếu chưa có `tools/dev/compile/build`).
- Đọc mục phiên bản mới nhất trong `docs/TIEN_TRINH_DU_AN.md` để biết đợt này vẽ gì (`DOT`, `VE_LAI`, mục mới trong script).
- Ghi lại mốc giờ của lần nhập trước (giờ UTC trong tài liệu hoặc lần commit gần nhất) để chỉ tải ảnh MỚI.

## 1. Tải ảnh mới (giao cho subagent, đừng tự tải để đỡ tốn ngữ cảnh)
- Thư mục Drive: `DuaXeSinhTon_assets_v2` (id `1nPaaucPvla5PKYdqUsBh9t1s_Q3mZHSB`, nằm trong `14i1_kbJc9H1rZfqnH9T_pj3xFs0gynEw`), thư mục con theo nhóm (nhan_vat, zombie, xe, xe_vu_khi, sung_xe, vu_khi, do_dac, hieu_ung, nen, khung_hinh…).
- Giao Agent (general-purpose, chạy nền) với lời dặn:
  - ToolSearch `select:mcp__Google_Drive__search_files,mcp__Google_Drive__download_file_content`.
  - Liệt kê PNG có `modifiedTime > '<mốc>'` (phân trang), xác định nhóm theo thư mục cha; bỏ `khung_hinh/_xuong`, txt, mp3, zip. Trùng tên lấy bản mới nhất. Nếu tất cả file đều "mới" (thư mục bị tạo lại) thì báo lại trước khi tải hết.
  - `download_file_content` từng id, giải mã base64 (trả thẳng hoặc trong JSON ở tool-results — chỉ đọc kết quả của lượt tải này vì file vẽ lại có thể giữ id cũ), ghi `/tmp/claude-0/dN/<nhóm>/<tên>.png` (thư mục MỚI, rỗng); kiểm tra đầu `\x89PNG` và dung lượng khớp `fileSize`. Script để ở `/tmp/claude-0/scripts/`, chạy `python3 -I`.
  - KHÔNG mở bảng "Key API"/file chứa key, không sửa Drive, không đụng repo.

## 2. Kiểm tra từng ảnh (bắt buộc, bằng mắt)
- Ghép bảng xem: `python3 -I tools/assets/xem_anh.py /tmp/claude-0/xem.png /tmp/claude-0/dN/*/*.png` rồi Read ảnh.
- Với mỗi ảnh xét: đúng nội dung? một vật duy nhất? quay PHẢI (đầu xe / mặt / nòng súng)? nền sạch? cùng phong cách truyện tranh?
- Xử lý bằng các bảng trong `tools/assets/nhap_asset.py` (khối `if V2:`):
  - quay trái → thêm vào `FACING_LEFT`; dựng đứng mũi lên → `TIP_UP`;
  - AI vẽ thêm bản nhỏ → `ONLY_BIGGEST`; nhiều bản xếp chồng → `TOP_BAND`;
  - dính vật thừa một bên → `CUT_LEFT` / `CUT_RIGHT` (tỉ lệ giữ lại);
  - ảnh hỏng hẳn → KHÔNG nhập (giữ ảnh cũ hoặc hình vẽ code), ghi vào `VE_LAI` đợt sau của script (đổi `DOT`).
- Hiệu ứng (fx_*) có nền phong cảnh: tách bằng độ rực màu như các đợt trước rồi mới nhập.
- Nhóm `xe_vu_khi` tự dùng FACING_LEFT/ONLY_BIGGEST của xe gốc; nhân vật dáng người phải 2 chân tách rời (game cắt chân ở hông + gối).

## 3. Cài vào game
- Gom ảnh được duyệt vào thư mục nhập (giữ thư mục nhóm), ví dụ thêm vào `/tmp/claude-0/stage3/<nhóm>/` (bộ đang dùng) rồi chạy:
  `python3 -I tools/assets/nhap_asset.py /tmp/claude-0/stage3 --v2`
  (sprite ≤ 512px, khung hình 400px, nền PNG; tự tạo ảnh dẫn xuất như `survivor_body`).
- Ảnh có TÊN MỚI chưa dùng trong code → nối vào code (Sprites.kt / Renderer.kt / CampDraw.kt / SpriteDraw.kt) theo kiểu "có ảnh thì dùng, chưa có thì vẽ code". Ảnh xe mới đổi vị trí bánh → đo lại `Sprites.WHEELS`. Ảnh tài xế mới → kiểm tra vị trí bàn tay trong `heldWeapon`.
- `cd tools/dev/compile && gradle compileKotlin`; `cd tools/dev/preview && gradle camp`; xem ảnh `tools/dev/out/shots/` (menu, trại, đêm, lục soát, lái xe, phòng trùm) — chú ý hướng quay, kích thước, bánh xe.

## 4. Phát hành
- Tăng `versionCode`/`versionName` trong `app/build.gradle.kts` và `Game.VERSION`; `bash tools/dev/build_apk.sh <code> <name>`.
- Thêm mục phiên bản vào `docs/TIEN_TRINH_DU_AN.md` (ảnh dùng / bỏ và lý do, bảng sửa hướng, mốc giờ Drive của đợt này), cập nhật dòng phiên ở "Các phiên chat"; `node tools/dev/md2docx.js docs/TIEN_TRINH_DU_AN.md docs/TIEN_TRINH_DU_AN.docx`.
- Commit, push nhánh làm việc; kiểm tra GitHub Actions "Build APK".
- Gửi người dùng: APK + ảnh chụp thử. Báo ngắn: ảnh đã cài, ảnh bỏ (vì sao), đề xuất đợt vẽ lại tiếp theo (theo quy tắc tự đề xuất ảnh AI trong CLAUDE.md); nếu có đợt mới thì cập nhật `DOT`/`VE_LAI` trong script và gửi kèm script.

## Trả lời
Luôn bằng tiếng Việt, ngắn gọn; đặt tiêu đề phiên theo CLAUDE.md.
