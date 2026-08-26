package com.encore.ticket.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 모듈을 하나로 합치면서 빌드가 막아주던 도메인 간 경계를 잃었다.
 * 그 자리를 이 규칙들이 대신한다.
 */
@AnalyzeClasses(packages = "com.encore.ticket.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * booking → catalog 같은 단방향 호출은 허용한다. 도메인을 합친 이유가 그것이다.
     * 다만 순환이 생기면 어느 쪽이 먼저인지 말할 수 없게 된다.
     */
    @ArchTest
    static final ArchRule 도메인끼리_순환하지_않는다 =
            slices().matching("com.encore.ticket.core.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule 도메인과_포트는_스프링을_모른다 =
            noClasses().that().resideInAPackage("com.encore.ticket.core..")
                    .and().resideOutsideOfPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..");

    /**
     * core 는 웹·영속성 기술을 모른다. 지금은 의존성 자체가 없어 빌드가 막지만,
     * 누가 core 에 그 의존을 추가하려 할 때 규칙 이름으로 먼저 알려준다.
     */
    @ArchTest
    static final ArchRule 도메인은_웹과_영속성_기술을_모른다 =
            noClasses().that().resideInAPackage("com.encore.ticket.core..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.web..",
                            "jakarta.servlet..",
                            "jakarta.persistence..",
                            "org.springframework.data..",
                            "org.springframework.transaction..");
}
