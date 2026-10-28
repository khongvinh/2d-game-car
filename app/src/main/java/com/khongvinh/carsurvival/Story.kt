package com.khongvinh.carsurvival

/** One screen of the story: a heading and a few paragraphs. */
class StoryPage(val title: String, val text: List<String>)

/**
 * "Biên niên sử Chiến Xa" — the zombie apocalypse the player drives through.
 * The prologue shows on first launch, a chapter when a region is first entered,
 * and the ending after the mothership falls. All of it can be re-read from the menu.
 */
object Story {
    const val PROLOGUE_BIT = 0
    const val ENDING_BIT = 5

    val prologue = listOf(
        StoryPage(
            "NGÀY 0 — CƠN MƯA ĐỎ",
            listOf(
                "Năm 2049, một trận mưa thiên thạch đỏ rực trút xuống Trái Đất suốt bảy ngày đêm.",
                "Bụi đỏ ngấm vào nước, vào đất, vào máu. Chỉ sau ba đêm, hàng tỷ người gục xuống… rồi đứng dậy, không còn là người.",
                "Thế giới gọi đó là MẠT THẾ.",
            ),
        ),
        StoryPage(
            "NGƯỜI THỢ MÁY CUỐI CÙNG",
            listOf(
                "Bạn là thợ máy của một thị trấn ven sa mạc. Trong gara đổ nát chỉ còn chiếc xe Cub cũ, khẩu súng lục và vài can xăng.",
                "Chiếc radio rè rè bắt được một tín hiệu: \"…Trạm Cứu Hộ… phía tây… vẫn còn người sống… hãy đến…\"",
            ),
        ),
        StoryPage(
            "VÒNG BÃO TÍM",
            listOf(
                "Sau lưng bạn, cơn bão bụi tím nuốt chửng mọi thứ trên đường đi — chính thứ bụi đã biến loài người thành xác sống.",
                "Dừng lại là chết. Thế giới mới chỉ còn một luật duy nhất:",
                "CHẠY CÀNG XA — CÀNG SỐNG SÓT!",
            ),
        ),
        StoryPage(
            "NGỌC ĐỎ VÀ CHIẾN XA",
            listOf(
                "Mảnh thiên thạch vỡ thành những viên ngọc đỏ vương vãi khắp đường. Chúng chứa nguồn năng lượng kỳ lạ: lắp vào động cơ, chiếc xe mạnh lên từng ngày.",
                "Nhặt ngọc, hạ quái lấy tiền, dựng chiến xa của riêng bạn — từ chiếc xe máy cũ cho tới xe tăng hủy diệt.",
            ),
        ),
    )

    /** Chapter shown the first time a run starts in [region]. */
    fun chapter(region: Region) = when (region) {
        Region.DESERT -> StoryPage(
            "CHƯƠNG 1 — SA MẠC CHẾT",
            listOf(
                "Trạm cứu hộ đầu tiên nằm cuối sa mạc. Bọn cướp đường đã chết từ lâu, nhưng lô cốt của chúng dọc lộ vẫn tự nhả đạn vào mọi thứ di chuyển.",
                "Ở cổng trạm, một GÃ KHỔNG LỒ — từng là đô vật nổi tiếng của thị trấn — đứng canh, ném cả tảng đá vào kẻ lạ.",
            ),
        )
        Region.VOLCANO -> StoryPage(
            "CHƯƠNG 2 — VÙNG ĐẤT LỬA",
            listOf(
                "Tín hiệu dẫn bạn vào dãy núi lửa vừa thức giấc. Một khối thiên thạch đỏ đã rơi trúng miệng núi.",
                "Từ dung nham sôi sục, thằn lằn lửa bò ra khắp nơi — và trong Hang Rồng, một con RỒNG LỬA đang canh giữ con đường duy nhất đi tiếp.",
            ),
        )
        Region.NIGHT -> StoryPage(
            "CHƯƠNG 3 — RỪNG ĐÊM VĨNH CỬU",
            listOf(
                "Bụi tím che kín mặt trời. Ở khu rừng nơi màn đêm không bao giờ dứt, xác sống lang thang giữa những ngôi làng bỏ hoang.",
                "Đàn dơi làm tổ trong lâu đài cổ. Chúa tể của chúng — DƠI HÚT MÁU — săn mọi chiếc xe còn sáng đèn.",
            ),
        )
        Region.ALIEN -> StoryPage(
            "CHƯƠNG 4 — HÀNH TINH LẠ",
            listOf(
                "Sự thật lộ diện: cơn mưa đỏ không phải tai nạn. Một con TÀU MẸ ngoài hành tinh đã gieo bụi đỏ để \"dọn sạch\" Trái Đất.",
                "Mặt đất nơi đây đã bị biến thành hành tinh của chúng. Hạ QUÁI VẬT NGOÀI HÀNH TINH trong tàu mẹ — cơ hội cuối cùng của loài người.",
            ),
        )
    }

    val ending = StoryPage(
        "KẾT — BÌNH MINH MỚI",
        listOf(
            "Tàu mẹ nổ tung giữa trời. Cơn bão tím tan dần, lần đầu tiên sau nhiều năm ánh mặt trời chiếu xuống mặt đất.",
            "Tại Trạm Cứu Hộ cuối cùng, những người sống sót reo hò đón chiếc chiến xa phủ đầy bụi và vết đạn.",
            "Nhưng radio lại rè lên… vẫn còn những vùng đất chưa được giải phóng. Gara của bạn giờ có XE TĂNG — hãy quay lại đường đua!",
        ),
    )

    /** Everything the player has unlocked so far, for re-reading from the menu. */
    fun unlocked(seen: Int, bestStage: Int): List<StoryPage> {
        val pages = ArrayList(prologue)
        for (r in Region.entries) if (seen and (1 shl (r.ordinal + 1)) != 0 || bestStage >= r.ordinal * 5) pages.add(chapter(r))
        if (seen and (1 shl ENDING_BIT) != 0) pages.add(ending)
        return pages
    }
}
