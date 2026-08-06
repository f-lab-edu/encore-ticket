plugins {
    id("ticket.java-library")
}

dependencies {
    // api: booking의 공개 DTO가 catalog.api.dto.PageResponse를 시그니처에 노출하므로(예매 목록 envelope),
    // 소비자(app)의 컴파일 클래스패스에도 catalog가 올라와야 한다. implementation이면 올라오지 않는다.
    api(projects.catalog)
}