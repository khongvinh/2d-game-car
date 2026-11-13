# Tuân thủ Google Play — quy tắc làm việc (bắt buộc cho mọi dự án game)

Người dùng chuẩn bị mở tài khoản nhà phát triển Google Play và đã chấp nhận Thỏa thuận Phân phối
dành cho Nhà phát triển (DDA). Mọi việc code, tạo asset, viết mô tả cửa hàng đều phải tuân theo các
quy tắc dưới đây. Đây là ghi chú làm việc, không phải tư vấn pháp lý.

## Tóm tắt DDA cần nhớ
- Người dùng giữ toàn bộ quyền sở hữu trí tuệ; cấp cho Google giấy phép không độc quyền để hiển thị,
  quảng bá, phân phối (Mục 4–5).
- Google có quyền gỡ ứng dụng / khóa tài khoản ngay nếu cho là vi phạm chính sách, bản quyền, gây hại
  thiết bị (Mục 10.3). Kháng cáo tài khoản cá nhân rất khó → PHÒNG NGỪA là chính.
- Nhà phát triển tự chịu và BỒI THƯỜNG mọi khiếu nại bên thứ ba: bản quyền hình/âm thanh/font/thư viện,
  lộ dữ liệu người dùng (Mục 14).
- Google miễn trách nhiệm khi Play lỗi/gián đoạn (Mục 12–13); tranh chấp theo luật California (Mục 16.8).
- Có doanh thu: Google thu phí dịch vụ và một số thuế giao dịch; người dùng tự kê khai thuế thu nhập
  cá nhân tại Việt Nam, giữ chứng từ nhận tiền (Mục 3).

## Quy tắc cho trợ lý khi làm game

### 1. Bản quyền nội dung (quan trọng nhất)
- KHÔNG sao chép, dùng lại hay mô phỏng sát hình ảnh, âm thanh, nhạc, tên, logo, nhân vật, giao diện của
  game/thương hiệu khác (ví dụ Earn to Die, Plants vs Zombies…). Chỉ học thể loại/cảm giác chơi.
- KHÔNG đưa tên game/thương hiệu khác vào: tên app, mô tả cửa hàng, từ khóa, ảnh chụp, prompt tạo ảnh
  dùng trong bản phát hành, chuỗi hiển thị trong game. (Ghi chú nội bộ trong tài liệu dev thì được.)
- Asset bên ngoài: chỉ dùng loại giấy phép cho phép thương mại (CC0, hoặc giấy phép ghi rõ dùng thương
  mại); ghi nguồn + giấy phép vào `docs/NGUON_TAI_NGUYEN.md` trước khi đưa vào game. CC-BY phải ghi công
  trong game. Không dùng asset "chỉ phi thương mại", không rõ giấy phép, hoặc lấy từ game khác.
- Ảnh AI (SDXL qua Colab): giấy phép mô hình (CreativeML Open RAIL++-M) cho phép dùng thương mại;
  prompt không nhắc tên tác giả/thương hiệu/nhân vật có bản quyền; ghi mô hình + ngày tạo vào
  `docs/NGUON_TAI_NGUYEN.md`.
- Âm thanh ElevenLabs: chỉ dùng thương mại nếu gói tài khoản của người dùng cho phép — kiểm tra trước
  khi phát hành; nếu không chắc thì thay bằng âm thanh tự tổng hợp (Sfx.kt) hoặc CC0.
- Font: chỉ font hệ thống hoặc font giấy phép mở (OFL…) có ghi nguồn. Emoji hệ thống thì được.
- Thư viện code: chỉ giấy phép tương thích (Apache-2.0, MIT, BSD…); liệt kê trong mục giấy phép mã
  nguồn mở trong app nếu có thư viện ngoài.

### 2. An toàn & chính sách
- Không mã độc, không tải/thực thi code từ xa, không quảng cáo lừa đảo, không thu thập dữ liệu ẩn.
- Chỉ xin quyền Android thật sự cần (hiện tại: KHÔNG xin quyền nào). Thêm quyền/SDK mới (quảng cáo,
  phân tích, đăng nhập…) phải báo người dùng vì kéo theo khai báo An toàn dữ liệu + chính sách quyền riêng tư.
- Bạo lực zombie kiểu hoạt hình: khai báo xếp hạng nội dung trung thực (IARC, dự kiến 12+/Teen).
- Nếu có mua trong app: chỉ qua Google Play Billing; giá, phần thưởng rõ ràng; không cơ chế lừa trẻ em.
- Không nhắm đối tượng trẻ em (chương trình Families có yêu cầu riêng) trừ khi người dùng quyết định.

### 3. Kỹ thuật phát hành
- Ký bằng khóa upload riêng (không dùng khóa debug), lưu trong GitHub Secrets; bật Play App Signing.
- Phát hành gói `.aab`; targetSdk theo yêu cầu hiện hành của Play; 64-bit.
- Cần: chính sách quyền riêng tư (URL), khai báo An toàn dữ liệu, icon 512, ảnh bìa 1024x500, ảnh chụp.
- Tài khoản cá nhân mới: thử nghiệm kín 12 người x 14 ngày trước khi lên chính thức.

### 4. Tài khoản (nhắc người dùng)
- Không dùng chung máy/thẻ/thông tin định danh với tài khoản Play từng bị khóa.
- Giữ chứng từ doanh thu để kê khai thuế.

## Việc trợ lý tự làm
- Trước mỗi bản phát hành lên Play: rà `app/src` + asset theo mục 1–3, báo các điểm rủi ro.
- Khi thêm asset/thư viện/quyền mới: cập nhật `docs/NGUON_TAI_NGUYEN.md` và cảnh báo nếu có rủi ro.
